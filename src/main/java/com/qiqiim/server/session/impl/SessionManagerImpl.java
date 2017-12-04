/**
 ***************************************************************************************
 *  @Author     1044053532@qq.com   
 *  @License    http://www.apache.org/licenses/LICENSE-2.0
 ***************************************************************************************
 */
package com.qiqiim.server.session.impl;

import io.netty.channel.ChannelHandlerContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.qiqiim.constant.Constants;
import com.qiqiim.server.group.ImChannelGroup;
import com.qiqiim.server.model.MessageWrapper;
import com.qiqiim.server.model.Session;
import com.qiqiim.server.model.proto.MessageProto;
import com.qiqiim.server.proxy.MessageProxy;
import com.qiqiim.server.session.SessionManager;

public class SessionManagerImpl implements SessionManager {

    private final static Logger log = LoggerFactory.getLogger(SessionManagerImpl.class);
    private MessageProxy proxy;
    
    public void setProxy(MessageProxy proxy) {
		this.proxy = proxy;
	}
    /**
     * The set of currently active Sessions for this Manager, keyed by session
     * identifier.
     */
    protected Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

    public synchronized void addSession(Session session) {
        if (null == session) {
            return;
        } 
        sessions.put(session.getAccount(), session);
        ImChannelGroup.add(session.getSession());
        //全员发送上线消息
        ImChannelGroup.broadcast(proxy.getOnLineStateMsg(session.getAccount()));
        log.debug("put a session " + session.getAccount() + " to sessions!");
    }

    public synchronized void updateSession(Session session) {
        //session.setLastAccessedTime(System.currentTimeMillis());
        session.setUpdateTime(System.currentTimeMillis());
        sessions.put(session.getAccount(), session);
    }

    /**
     * Remove this Session from the active Sessions for this Manager.
     */
    public synchronized void removeSession(String sessionId) {
    	try{
    		Session session = getSession(sessionId);
    		if(session!=null){
    			session.closeAll();
				sessions.remove(sessionId);
				ImChannelGroup.broadcast(proxy.getOffLineStateMsg(sessionId));
    		}  
    	}catch(Exception e){
    		
    	} 
    	log.info("system remove the session " + sessionId + " from sessions!");
    } 
    
    
    public synchronized void removeSession(String sessionId,String nid) {
    	try{
    		Session session = getSession(sessionId);
    		if(session!=null){
    			if(session.getSource()==Constants.ImserverConfig.WEBSOCKET){
    				session.close(nid);
    				//判断没有其它session后 从SessionManager里面移除
    				if(session.otherSessionSize()<1){
    					sessions.remove(sessionId);
    					ImChannelGroup.broadcast(proxy.getOffLineStateMsg(sessionId));
    				} 
    			}else{
    				session.close();
    				sessions.remove(sessionId);
    				ImChannelGroup.broadcast(proxy.getOffLineStateMsg(sessionId));
    			}
    		}  
    	}catch(Exception e){
    		
    	}finally{
    		
    		
    	}
        log.info("remove the session " + sessionId + " from sessions!");
    } 

    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public Session[] getSessions() {
        return sessions.values().toArray(new Session[0]);
    }

    public Set<String> getSessionKeys() {
        return sessions.keySet();
    }

    public int getSessionCount() {
        return sessions.size();
    }


    @Override
    public  Session createSession(MessageWrapper wrapper, ChannelHandlerContext ctx) {
    	String sessionId = wrapper.getSessionId();
        Session session = sessions.get(sessionId);
        if (session != null) {
        	log.info("session " + sessionId + " exist!");
        	//当链接来源不是同一来源或者 是socket链接，踢掉已经登录的session 
        	if(session.getSource()!=wrapper.getSource() || session.getSource()==Constants.ImserverConfig.SOCKET){
        		// 如果session已经存在则销毁session
                //从广播组清除
        		log.info("sessionId" + session.getNid() +"------------------"+ ctx.channel().id().asShortText()+ "      !");
                ImChannelGroup.remove(session.getSession());
                //session.close();
                sessions.remove(session.getAccount());
                log.info("session " + sessionId + " have been closed!");
        	}else if(session.getSource()==Constants.ImserverConfig.WEBSOCKET){
        		//用于解决websocket多开页面session被踢下线的问题
        		Session  newsession = setSessionContent(ctx,wrapper,sessionId);
        		session.addSessions(newsession);
        		updateSession(session);
        		ImChannelGroup.add(newsession.getSession());
                log.info("session " + sessionId + " update!");
        		return newsession;
        	}  
        }
       
        session = setSessionContent(ctx,wrapper,sessionId);
        addSession(session);
      
        return session;
    }
    
    /**
     * 设置session内容
     * @param ctx
     * @param wrapper
     * @param sessionId
     * @return
     */
    private Session  setSessionContent(ChannelHandlerContext ctx,MessageWrapper wrapper,String sessionId){
    	 log.info("create new session " + sessionId + ", ctx -> " + ctx.toString());
    	  MessageProto.Model model = (MessageProto.Model)wrapper.getBody();
    	  Session session = new Session(ctx.channel());
          session.setAccount(sessionId);
          session.setSource(wrapper.getSource());
          session.setAppKey(model.getAppKey());
          session.setDeviceId(model.getDeviceId());
          session.setPlatform(model.getPlatform());
          session.setPlatformVersion(model.getPlatformVersion());
          session.setSign(model.getSign());
          session.setBindTime(System.currentTimeMillis());
          session.setUpdateTime(session.getBindTime());
          log.info("create new session " + sessionId + " successful!");
          return session;
    }
    
    

	@Override
	public boolean exist(String sessionId) {
        Session session = getSession(sessionId);
        return session != null ? true : false;
	}


    


  /*  protected Connection createTcpConnection(Session session, ChannelHandlerContext ctx) {
        Connection conn = new TcpConnection(ctx);
        conn.setConnectionId(session.getSessionId());
        conn.setSession(session);
        return conn;
    }*/

}