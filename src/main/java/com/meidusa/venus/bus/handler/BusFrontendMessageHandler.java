package com.meidusa.venus.bus.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.meidusa.toolkit.common.poolable.InvalidVirtualPoolException;
import com.meidusa.toolkit.common.util.Tuple;
import com.meidusa.toolkit.net.BackendConnectionPool;
import com.meidusa.toolkit.net.ConnectionConnector;
import com.meidusa.toolkit.net.MessageHandler;
import com.meidusa.toolkit.net.util.InetAddressUtil;
import com.meidusa.toolkit.util.StringUtil;
import com.meidusa.venus.backend.ShutdownListener;
import com.meidusa.venus.backend.VenusStatus;
import com.meidusa.venus.bus.ServiceRemoteManager;
import com.meidusa.venus.bus.network.BusBackendConnection;
import com.meidusa.venus.bus.network.BusFrontendConnection;
import com.meidusa.venus.exception.VenusExceptionCodeConstant;
import com.meidusa.venus.io.packet.AbstractServicePacket;
import com.meidusa.venus.io.packet.ErrorPacket;
import com.meidusa.venus.io.packet.PacketConstant;
import com.meidusa.venus.io.packet.PingPacket;
import com.meidusa.venus.io.packet.PongPacket;
import com.meidusa.venus.io.packet.ServiceAPIPacket;
import com.meidusa.venus.io.packet.ServicePacketBuffer;
import com.meidusa.venus.io.packet.VenusRouterPacket;
import com.meidusa.venus.io.packet.VenusStatusRequestPacket;
import com.meidusa.venus.io.packet.VenusStatusResponsePacket;
import com.meidusa.venus.util.Range;

/**
 * 前端消息处理,负责接收服务请求
 * 
 * @author structchen
 * 
 */
public class BusFrontendMessageHandler implements MessageHandler<BusFrontendConnection, byte[]> {
    private static Logger logger = LoggerFactory.getLogger(BusFrontendMessageHandler.class);
    private static ShutdownListener listener = new ShutdownListener();
    static {
        Runtime.getRuntime().addShutdownHook(listener);
    }

    private ServiceRemoteManager remoteManager;

    private ConnectionConnector connector;

    public ConnectionConnector getConnector() {
        return connector;
    }

    public void setConnector(ConnectionConnector connector) {
        this.connector = connector;
    }

    public ServiceRemoteManager getRemoteManager() {
        return remoteManager;
    }

    public void setRemoteManager(ServiceRemoteManager remoteManager) {
        this.remoteManager = remoteManager;
    }

    @Override
    public void handle(BusFrontendConnection conn, final byte[] message) {
        int type = AbstractServicePacket.getType(message);
        switch (type) {
            case PacketConstant.PACKET_TYPE_PING:
                PingPacket ping = new PingPacket();
                ping.init(message);
                PongPacket pong = new PongPacket();
                AbstractServicePacket.copyHead(ping, pong);
                conn.write(pong.toByteBuffer());
                if (logger.isDebugEnabled()) {
                    logger.debug("receive ping packet from " + conn.getId());
                }
                break;

            // ignore this packet
            case PacketConstant.PACKET_TYPE_PONG:

                break;
            case PacketConstant.PACKET_TYPE_VENUS_STATUS_REQUEST:
                VenusStatusRequestPacket sr = new VenusStatusRequestPacket();
                sr.init(message);
                VenusStatusResponsePacket response = new VenusStatusResponsePacket();
                AbstractServicePacket.copyHead(sr, response);

                response.status = VenusStatus.getInstance().getStatus();
                conn.write(response.toByteBuffer());
                break;
            case PacketConstant.PACKET_TYPE_SERVICE_REQUEST: {
                ServicePacketBuffer packetBuffer = new ServicePacketBuffer(message);

                try {
                    VenusRouterPacket routerPacket = new VenusRouterPacket();
                    routerPacket.srcIP = InetAddressUtil.pack(conn.getInetAddress().getAddress());
                    routerPacket.data = message;
                    routerPacket.frontendConnectionID = conn.getSequenceID();
                    routerPacket.frontendRequestID = conn.getNextRequestID();
                    routerPacket.serializeType = conn.getSerializeType();
                    try {
                        packetBuffer.skip(PacketConstant.SERVICE_HEADER_SIZE + 8);
                        final String apiName = packetBuffer.readLengthCodedString(PacketConstant.PACKET_CHARSET);
                        final int serviceVersion = packetBuffer.readInt();
                        int index = apiName.lastIndexOf(".");
                        String serviceName = apiName.substring(0, index);
                        // String methodName = apiName.substring(index + 1);
                        List<Tuple<Range, BackendConnectionPool>> list = remoteManager.getRemoteList(serviceName);

                        // service not found
                        if (list == null || list.size() == 0) {
                            ServiceAPIPacket apiPacket = new ServiceAPIPacket();
                            packetBuffer.reset();
                            apiPacket.init(packetBuffer);
                            ErrorPacket error = new ErrorPacket();
                            AbstractServicePacket.copyHead(apiPacket, error);
                            error.errorCode = VenusExceptionCodeConstant.SERVICE_NOT_FOUND;
                            error.message = "service not found :" + serviceName;
                            conn.write(error.toByteBuffer());
                            return;
                        }

                        for (Tuple<Range, BackendConnectionPool> tuple : list) {

                            if (tuple.left.contains(serviceVersion)) {
                                BusBackendConnection remoteConn = null;
                                try {
                                    remoteConn = (BusBackendConnection) tuple.right.borrowObject();
                                    routerPacket.backendRequestID = remoteConn.getNextRequestID();
                                    remoteConn.addRequest(routerPacket.backendRequestID, routerPacket.frontendConnectionID, routerPacket.frontendRequestID);
                                    conn.addUnCompleted(routerPacket.frontendRequestID, routerPacket);
                                    remoteConn.write(VenusRouterPacket.toByteBuffer(routerPacket));
                                    return;
                                } catch(InvalidVirtualPoolException e){
                                	ServiceAPIPacket apiPacket = new ServiceAPIPacket();
                                    packetBuffer.reset();
                                    apiPacket.init(packetBuffer);
                                	ErrorPacket error = new ErrorPacket();
                                    AbstractServicePacket.copyHead(apiPacket, error);
                                    error.errorCode = VenusExceptionCodeConstant.SERVICE_UNAVAILABLE_EXCEPTION;
                                    error.message = e.getMessage();
                                    conn.write(error.toByteBuffer());
                                    return;
                                }catch (Exception e) {
                                	conn.addUnCompleted(routerPacket.frontendRequestID, routerPacket);
                                    conn.getRetryHandler().addRetry(conn, routerPacket);
                                    return;
                                } finally {
                                    if (remoteConn != null) {
                                        tuple.right.returnObject(remoteConn);
                                    }
                                }
                            }
                        }

                        // Service version not match

                        ServiceAPIPacket apiPacket = new ServiceAPIPacket();
                        packetBuffer.reset();
                        apiPacket.init(packetBuffer);

                        ErrorPacket error = new ErrorPacket();
                        AbstractServicePacket.copyHead(apiPacket, error);
                        error.errorCode = VenusExceptionCodeConstant.SERVICE_VERSION_NOT_ALLOWD_EXCEPTION;
                        error.message = "Service version not match";
                        conn.write(error.toByteBuffer());

                    } catch (Exception e) {
                        ServiceAPIPacket apiPacket = new ServiceAPIPacket();
                        packetBuffer.reset();
                        apiPacket.init(packetBuffer);

                        logger.error("decode error", e);
                        ErrorPacket error = new ErrorPacket();
                        AbstractServicePacket.copyHead(apiPacket, error);
                        error.errorCode = VenusExceptionCodeConstant.PACKET_DECODE_EXCEPTION;
                        error.message = "decode packet exception:" + e.getMessage();
                        conn.write(error.toByteBuffer());
                        return;
                    }

                } catch (Exception e) {
                    ServiceAPIPacket apiPacket = new ServiceAPIPacket();
                    packetBuffer.reset();
                    apiPacket.init(packetBuffer);

                    ErrorPacket error = new ErrorPacket();
                    AbstractServicePacket.copyHead(apiPacket, error);
                    error.errorCode = VenusExceptionCodeConstant.SERVICE_UNAVAILABLE_EXCEPTION;
                    error.message = e.getMessage();
                    conn.write(error.toByteBuffer());
                    logger.error("error when invoke", e);
                    return;
                } catch (Error e) {

                    ServiceAPIPacket apiPacket = new ServiceAPIPacket();
                    packetBuffer.reset();
                    apiPacket.init(packetBuffer);

                    ErrorPacket error = new ErrorPacket();
                    AbstractServicePacket.copyHead(apiPacket, error);
                    error.errorCode = VenusExceptionCodeConstant.SERVICE_UNAVAILABLE_EXCEPTION;
                    error.message = e.getMessage();
                    conn.write(error.toByteBuffer());
                    logger.error("error when invoke", e);
                    return;
                }
                break;
            }
            case PacketConstant.AUTHEN_TYPE_PASSWORD:

                break;
            default:
                StringBuilder buffer = new StringBuilder("receive unknown type packet from ");
                buffer.append(conn.getId()).append("\n");
                buffer.append("-------------------------------").append("\n");
                buffer.append(StringUtil.dumpAsHex(message, message.length)).append("\n");
                buffer.append("-------------------------------").append("\n");
                logger.warn(buffer.toString());

        }
    }

}
