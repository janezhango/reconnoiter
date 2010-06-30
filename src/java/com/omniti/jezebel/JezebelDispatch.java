package com.omniti.jezebel;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.Hashtable;
import javax.servlet.*;
import javax.servlet.http.*;
import org.w3c.dom.*;
import org.xml.sax.*;
import javax.xml.parsers.*;

import com.omniti.jezebel.Resmon;
import com.omniti.jezebel.ResmonResult;
import com.omniti.jezebel.JezebelCheck;
import com.omniti.jezebel.JezebelClassLoader;

public class JezebelDispatch extends HttpServlet {

    protected class JezebelInputSyntax extends Exception { }

    static DocumentBuilderFactory builderfactory =
      DocumentBuilderFactory.newInstance();
    static ClassLoader cl =
      new JezebelClassLoader(JezebelDispatch.class.getClassLoader());

    DocumentBuilder builder;

    public JezebelDispatch() {
      try { builder = builderfactory.newDocumentBuilder(); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
    public void doPost(HttpServletRequest request,
                       HttpServletResponse response) {
      Document postDoc;
      Hashtable<String,String> info, config;

      try {
        postDoc = builder.parse(request.getInputStream());
        info = constructCheckInfo(postDoc);
        config = constructCheckConfig(postDoc);
      }
      catch (Exception e) { robustE(e, response); return; }

      String clz = request.getPathInfo().substring(1);
      if(clz.indexOf('.') == -1) clz = "com.omniti.jezebel.check." + clz;
      Class clazz = null;
      try { clazz = cl.loadClass(clz); }
      catch (ClassNotFoundException e) { robustE(e, response); return; }
      boolean found = false;
      Class ifaces[] = clazz.getInterfaces();
      if(ifaces != null) {
        for(int i=0; i<ifaces.length; i++)
          if(ifaces[i] == JezebelCheck.class) {
            found = true;
            break;
          }
      }
      if(!found) {
        try {
          PrintWriter writer = response.getWriter();
          writer.println(clz + " does not implement JezebelCheck interface");
          response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
        catch (IOException ioe) { }
        return;
      }

      JezebelCheck check = null;
      try { check = (JezebelCheck) clazz.newInstance(); }
      catch (InstantiationException e) { robustE(e, response); return; }
      catch (IllegalAccessException e) { robustE(e, response); return; }
      Resmon r = new Resmon();
      ResmonResult rr = r.addResult(info.get("module"), info.get("name"));
      check.perform(info, config, rr);
      r.write(response);
    }
    private Hashtable<String,String> constructCheckConfig(Document d)
        throws JezebelInputSyntax {
      Hashtable<String,String> o = new Hashtable<String,String>();
      Element root = d.getDocumentElement();
      if(!root.getTagName().equals("check")) { throw new JezebelInputSyntax(); }
      NodeList config = root.getElementsByTagName("config");
      if(config == null || config.getLength() == 0) return o;
      if(config.getLength() > 1) { throw new JezebelInputSyntax(); }
      NodeList l = config.item(0).getChildNodes();
      for(int i = 0; i < l.getLength(); i++) {
        Node n = l.item(i);
        if(n.getNodeType() == Node.ELEMENT_NODE) {
          o.put(n.getNodeName(), n.getTextContent());
        }
      }
      return o;
    }
    private Hashtable<String,String> constructCheckInfo(Document d)
        throws JezebelInputSyntax {
      /* These are first-class attributes of the check */
      /* module, name, period, target, timeout */
      Hashtable<String,String> o = new Hashtable<String,String>();
      Element root = d.getDocumentElement();
      if(!root.getTagName().equals("check")) { throw new JezebelInputSyntax(); }
      o.put("module", root.getAttribute("module"));
      o.put("name", root.getAttribute("name"));
      o.put("target", root.getAttribute("target"));
      o.put("timeout", root.getAttribute("timeout"));
      o.put("period", root.getAttribute("period"));
      return o;
    }
    private void robustE(Exception e, HttpServletResponse response) {
        Throwable cause = e.getCause();
        try {
          PrintWriter writer = response.getWriter();
          if(cause != null) cause.printStackTrace(writer);
          e.printStackTrace(writer);
          response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
        catch (IOException ioe) { }
    }
}
