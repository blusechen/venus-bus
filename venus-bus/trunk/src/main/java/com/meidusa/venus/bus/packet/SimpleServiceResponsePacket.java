package com.meidusa.venus.bus.packet;

import java.io.UnsupportedEncodingException;

import com.meidusa.venus.io.packet.ServicePacketBuffer;
import com.meidusa.venus.io.packet.ServiceResponsePacket;
import com.meidusa.venus.io.utils.GZipUtil;

public class SimpleServiceResponsePacket extends ServiceResponsePacket {
    private static final long serialVersionUID = 1L;

    /**
     * 用于跟踪请求的标记
     */
    public byte[] traceId;

    public byte[] response;
    public SimpleServiceResponsePacket() {
    }

    protected void readBody(ServicePacketBuffer buffer) {
        super.readBody(buffer);
        if (buffer.hasRemaining()) {
            byte f = (byte) (this.flags & CAPABILITY_GZIP);
            response = buffer.readLengthCodedBytes();
            if (response != null & response.length > 0) {
                if (f == CAPABILITY_GZIP) {
                	response = GZipUtil.decompress(response);
                }
            }
        }

        // 兼容3.0.1之前的版本,3.0.2与之后的版本将支持traceID
        if (buffer.hasRemaining()) {
            traceId = new byte[16];
            buffer.readBytes(traceId);
        }
    }

    @Override
    protected void writeBody(ServicePacketBuffer buffer) throws UnsupportedEncodingException {
        super.writeBody(buffer);
        //
        throw new UnsupportedOperationException();
    }

}
