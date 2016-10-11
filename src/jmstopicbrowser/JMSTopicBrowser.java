/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package jmstopicbrowser;

import java.util.Hashtable;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import com.bea.wlcp.wlng.api.edr.EdrData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author bbleonar_us
 */
public class JMSTopicBrowser implements MessageListener {

    public final static String JNDI_FACTORY = "weblogic.jndi.WLInitialContextFactory";
    public final static String JMS_FACTORY = "com.bea.wlcp.wlng.edr.EdrConnectionFactory";
    public final static String TOPIC = "com.bea.wlcp.wlng.edr.EdrTopic";
    private TopicConnectionFactory tconFactory;
    private TopicConnection tcon;
    private TopicSession tsession;
    private TopicSubscriber tsubscriber;
    private Topic topic;
    private boolean quit = false;

    @Override
    public void onMessage(Message msg) {
        try {
            String msgText = "";
            
            // This is where we expect to be...
            if (msg instanceof ObjectMessage) {
                System.out.println("\n=== ObjectMessage Found! ===");
                ObjectMessage omsg = (ObjectMessage) msg;

                // Get EDR data from serialized object...
                EdrData[] edrData = (EdrData[]) omsg.getObject();
                
                for (EdrData edr : edrData) {
                    
                    Map edrMap = edr.getMap();
                    
                    // Print all key / value pairs in the map...
                    System.out.println("\n== Printing key value pairs from EDR ==");
                    Iterator entries = edrMap.entrySet().iterator();
                    while (entries.hasNext()) {
                        {
                            Entry entry = (Entry) entries.next();
                            System.out.println(entry.getKey() + " /" + entry.getValue());                            
                        }
                    }
                    
                    // Find record with information we want to track...
                    if (edrMap.get("ReqAction") != null && edrMap.get("status") != null && edrMap.get("HttpStatusCode") != null) {
                        
                        System.out.println ("= Tracking record above = ");
                        
                        String serviceName = (edrMap.get("ServiceName") != null) ? edrMap.get("ServiceName").toString() : "unknown";
                        String httpStatusCode = (edrMap.get("HttpStatusCode") != null) ? edrMap.get("HttpStatusCode").toString() : "unknown";
                        String httpMethod = (edrMap.get("HttpMethod") != null) ? edrMap.get("HttpMethod").toString() : "unknown";
                        String status = (edrMap.get("status") != null) ? edrMap.get("status").toString() : "unknown";
                                                
                        String result = "Service " + serviceName + " using HTTP Method " + httpMethod + " completed with HTTP State Code " + httpStatusCode + " with status " + status + ".";
                        ArrayList <String> actions = (ArrayList) edrMap.get("ReqAction");
                        for (String action : actions) {
                            //There's just a single string in the array
                            //System.out.println("action="+action);                            
                        }
                        msgText = result;
                    }
                }

            } else {
                System.out.println("Something else found.");
                msgText = msg.toString();
            }
            System.out.println("\n" + msgText);
            if (msgText.equalsIgnoreCase("quit")) {
                synchronized (this) {
                    quit = true;
                    this.notifyAll(); // Notify main thread to quit
                }
            }
        } catch (JMSException jmse) {
            jmse.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void init(Context ctx, String topicName) throws NamingException, JMSException {
        tconFactory = (TopicConnectionFactory) ctx.lookup(JMS_FACTORY);
        tcon = tconFactory.createTopicConnection();
        tsession = tcon.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
        topic = (Topic) ctx.lookup(topicName);
        tsubscriber = tsession.createSubscriber(topic);
        tsubscriber.setMessageListener(this);
        tcon.start();
    }

    public void close() throws JMSException {
        tsubscriber.close();
        tsession.close();
        tcon.close();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Usage: java JMSTopicBrowser WebLogicURL");
            return;
        }
        InitialContext ic = getInitialContext(args[0]);

        JMSTopicBrowser tb = new JMSTopicBrowser();
        tb.init(ic, TOPIC);
        System.out.println("JMS Ready To Receive Messages.");
        // Wait until a "quit" message has been received.
        synchronized (tb) {
            while (!tb.quit) {
                try {
                    tb.wait();
                } catch (InterruptedException ie) {
                }
            }
        }
        tb.close();
    }

    private static InitialContext getInitialContext(String url) throws NamingException {
        Hashtable env = new Hashtable();
        env.put(Context.INITIAL_CONTEXT_FACTORY, JNDI_FACTORY);
        env.put("java.naming.provider.url", url);
        return new InitialContext(env);
    }

}
