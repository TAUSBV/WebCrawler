package net.taus.webcrawler.filter;

import java.net.URI;

import com.brandwatch.robots.RobotsConfig;
import com.brandwatch.robots.RobotsFactory;
import com.brandwatch.robots.RobotsService;

public class RobotsFilter implements Filter<String> {
	
	static RobotsConfig config;
	static RobotsFactory factory;
	static RobotsService service;
	
	String crawlerAgent = "Mozilla/5.0 (compatible; Linux x86_64; en-GB)";
		
	public RobotsFilter() {
		config = new RobotsConfig();
		config.setCacheExpiresHours(48);
		config.setCacheMaxSizeRecords(10000);
		factory = new RobotsFactory(config);
		service = factory.createService();
	}

	@Override
	public boolean execute(String url) {
		URI resource = URI.create(url);
		return service.isAllowed(crawlerAgent, resource);
	}

	
}
