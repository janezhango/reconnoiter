package com.omniti.jezebel.check;

import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.Properties;
import java.util.Date;
import java.sql.*;
import com.omniti.jezebel.ResmonResult;
import com.omniti.jezebel.JezebelCheck;
import com.omniti.jezebel.JezebelTools;

public abstract class JDBC implements JezebelCheck {
  public JDBC() { }
  protected abstract String jdbcDriverName();
  protected abstract String defaultPort();

  public void perform(Map<String,String> check,
                      Map<String,String> config,
                      ResmonResult rr) {
    String database = config.remove("database");
    String username = config.remove("user");
    String password = config.remove("password");
    String port = config.remove("port");
    if(port == null) port = defaultPort();
    String sql = config.remove("sql");
    String url = "jdbc:" + jdbcDriverName() + "://" +
                 check.get("target") + ":" + port + "/" + database;
    Properties props = new Properties();
    props.setProperty("user", username == null ? "" : username);
    props.setProperty("password", password == null ? "" : password);
    Set<Map.Entry<String,String>> set;
    set = config.entrySet();
    if(set != null) {
      Iterator<Map.Entry<String,String>> i = set.iterator();
      while(i.hasNext()) {
        Map.Entry<String,String> e = i.next();
        String key = e.getKey();
        if(key.startsWith("jdbc_")) {
          config.remove(key);
          props.setProperty(key.substring(5), e.getValue());
        }
      }
    }
    sql = JezebelTools.interpolate(sql, check, config);

    Connection conn = null;
    try {
      Date t1 = new Date();
      conn = DriverManager.getConnection(url, props);
      Date t2 = new Date();
      rr.set("connect_duration", (double)(t2.getTime() - t1.getTime())/1000.0);
      queryToResmon(conn, sql, rr);
      Date t3 = new Date();
      rr.set("query_duration", (double)(t3.getTime() - t2.getTime())/1000.0);
    }
    catch (SQLException e) { rr.set("jezebel_status", e.getMessage()); }
    finally {
      try { if(conn != null) conn.close(); }
      catch (SQLException e) { }
    }
  }
  protected void queryToResmon(Connection conn, String sql, ResmonResult rr) {
    int nrows = 0;
    Statement st = null;
    ResultSet rs = null;
    try {
      st = conn.createStatement();
      rs = st.executeQuery(sql);
      while (rs.next()) {
        ResultSetMetaData rsmd = rs.getMetaData();
        int ncols = rsmd.getColumnCount();
  
        nrows++;
        if(ncols < 2) continue;
        String prefix = rs.getString(1);
        for(int i = 2; i <= ncols; i++) {
          String name = prefix + '`' + rsmd.getColumnName(i);
          try {
            switch(rsmd.getColumnType(i)) {
              case Types.BOOLEAN:
                rr.set(name, rs.getBoolean(i) ? 1 : 0);
                break;
              case Types.TINYINT:
              case Types.SMALLINT:
              case Types.REAL:
              case Types.INTEGER:
              case Types.BIGINT:
                rr.set(name, rs.getLong(i));
                break;
              case Types.NUMERIC:
              case Types.FLOAT:
              case Types.DECIMAL:
              case Types.DOUBLE:
                rr.set(name, rs.getDouble(i));
                break;
              default:
                rr.set(name, rs.getString(i));
                break;
            }
          }
          catch (SQLException e) { rr.set("jezebel_status", e.getMessage()); }
        }
      }
    }
    catch (SQLException e) { rr.set("jezebel_status", e.getMessage()); }
    finally {
      try { rs.close(); } catch (Exception e) {}
      try { st.close(); } catch (Exception e) {}
    }
    rr.set("row_count", nrows);
  }
}
