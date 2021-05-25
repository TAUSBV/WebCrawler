package net.taus.webcrawler.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexFilter implements Filter<String> {
	
	List<Pattern> patterns = null;
	
	public RegexFilter(Properties prop) {
		patterns = new ArrayList<Pattern>();
		for(Object p : prop.keySet()) {
			if(p.toString().startsWith("pattern")) {
				this.patterns.add(Pattern.compile(prop.getProperty(p.toString(), "").trim()));
			}
		}
	}
	
	@Override
	public boolean execute(String toMatch) {
		boolean match = true;
		for(Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(toMatch);
			match = matcher.matches();
			if(!match) break;
		}
		return match;
	}

}
