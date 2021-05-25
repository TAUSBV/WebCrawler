package net.taus.webcrawler;

public class Link {
	
	String URL = null;
	int depth = 0;
	
	public Link(String URL, int depth) {
		this.URL = URL;
		this.depth = depth;
	}
	
	public String getURL() {
		return this.URL;
	}
	
	public int getDepth() {
		return this.depth;
	}

}
