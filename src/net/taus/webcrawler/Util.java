package net.taus.webcrawler;

public class Util {
	
	public static String getDriver(String basePATH, String browserName) {
		
		String os = System.getProperty("os.name").toLowerCase();
		if(os.contains("windows")) {
			return basePATH + "/windows/" + browserName;
		} else
		if(os.contains("linux")) {
			return basePATH + "/linux/" + browserName;
		} else 
		if(os.contains("mac")) {
			return basePATH + "/mac/" + browserName;
		} else {
			return null;
		}
	}
	

}
