package net.taus.webcrawler.filter;

import java.util.HashMap;
import java.util.Map;

public class URLFilter implements Filter<String> {
	
	Map<String, Filter<String>> filters = new HashMap<String, Filter<String>>(); 

	public void addFilter(String name, Filter<String> filter) {
		this.filters.put(name, filter);
	}
	
	public void removeFilter(String name) {
		this.filters.remove(name);
	}

	@Override
	public boolean execute(String toFilter) {
		boolean isAllowed = true;
		for(Filter<String> filter : filters.values()) {
			isAllowed = filter.execute(toFilter);
			if(!isAllowed) break;
		}
		return isAllowed;
	}
	
}
