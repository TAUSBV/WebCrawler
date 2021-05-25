package net.taus.webcrawler;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.HashSet;

import net.lightbody.bmp.filters.ResponseFilter;
import net.lightbody.bmp.filters.ResponseFilterAdapter;
import org.openqa.selenium.Proxy;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;


public class BrowserProxy extends BrowserMobProxyServer {
	
	private HashSet<String> shortCircuit = new HashSet<String>();	
	private HashSet<String> visited = new HashSet<String>();
	
	public Proxy getSeleniumProxy() {
		Proxy seleniumProxy = ClientUtil.createSeleniumProxy(this);
		try {
			String hostIp = Inet4Address.getLocalHost().getHostAddress();
			seleniumProxy.setHttpProxy(hostIp + ":" + this.getPort());
			seleniumProxy.setSslProxy(hostIp + ":" + this.getPort());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return seleniumProxy;
	}		
	
	public void addShortCircuitRequestFilter() {		
		this.addRequestFilter((request, contents, messageInfo) -> {
			//String url = messageInfo.getOriginalUrl();
			if(this.shortCircuit.contains(request.headers().get("crawler-identifier"))) {
    				io.netty.handler.codec.http.HttpResponse resp = new DefaultFullHttpResponse(request.getProtocolVersion(), HttpResponseStatus.ACCEPTED);
    				HttpHeaders.setContentLength(resp, 0L);
    				return resp;
			} else {
				//System.out.println("Proxy :: " + url);
				return null;
			}
		});

		this.addResponseFilter((response, contents, messageInfo) -> {
			//String url = messageInfo.getOriginalUrl();
		});		
				
	}

	public void addResponseFilter(ResponseFilter filter) {
		this.addLastHttpFilterFactory(new ResponseFilterAdapter.FilterSource(filter, Integer.MAX_VALUE));
	}
	
	public void enableShortCircuit(String identifier) {
		this.shortCircuit.add(identifier);
	}
	
	public void disableShortCircuit(String identifier) {
		this.shortCircuit.remove(identifier);
	}
	
}
