package com.meidusa.venus.bus.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.log4j.Logger;

public class MysqlConnection {
	private static Logger logger = Logger.getLogger(MysqlConnection.class);
	private Connection conn;
	static {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException e) {
			logger.fatal("mysql driver not found? Could not happened");
		}

	}

	public MysqlConnection() {
		super();
	}

	public void connect(String jdbcUrl) {
		try {
			conn = DriverManager.getConnection(jdbcUrl);
		} catch (SQLException e) {
			logger.fatal("could not connect to mysql " + jdbcUrl);
		}
	}

	public void close() {
		try {
			conn.close();
		} catch (SQLException e) {
			logger.fatal("could not close connection to mysql.");
		}
	}

	public ResultSet executeQuery(String sql) {
		Statement stmt;
		try {
			stmt = conn.createStatement();
			return stmt.executeQuery(sql);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			logger.fatal("could not execute sql " + sql + " on mysql server");
			return null;
		}
	}

}
