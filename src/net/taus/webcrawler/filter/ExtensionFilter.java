package net.taus.webcrawler.filter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.FilenameUtils;

public class ExtensionFilter implements Filter<String> {
	
	List<String> extensions = null;
	
	public ExtensionFilter(Properties prop) {
		extensions = new ArrayList<String>();
		String extToIgnore = prop.getProperty("ignore");
		for(String ext : extToIgnore.split("\\|")) {
			extensions.add(ext);
		}
	}
	
	@Override
	public boolean execute(String toMatch) {
		boolean ignore = false;
		try {
			URL url = new URL(toMatch);
			for(String ext : extensions) {
				String fileExt = FilenameUtils.getExtension(url.getPath());
				if(ext.equalsIgnoreCase(fileExt)) {
					ignore = true;
					break;
				}
			}
		}catch(MalformedURLException e) {
			ignore = true;
		}
		return !ignore;
	}

}
