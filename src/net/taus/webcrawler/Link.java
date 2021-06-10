package net.taus.webcrawler;

public class Link {

	String parent = null;
	String URL = null;
	int depth = 0;
	
	public Link(String URL, String parent, int depth) {
		this.URL = URL;
		this.parent = parent;
		this.depth = depth;
	}
	
	public String getURL() {
		return this.URL;
	}
	
	public int getDepth() {
		return this.depth;
	}

	public String getParent() {
		return this.parent;
	}

}
