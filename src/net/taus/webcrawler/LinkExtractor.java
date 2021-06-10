package net.taus.webcrawler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

public class LinkExtractor {
	
	
	public static List<List<WebElement>> extract(WebDriver driver, boolean crawlFollowAjax) {
		List<WebElement> linksAjax = new ArrayList<WebElement>();
		List<WebElement> linksNormal = new ArrayList<WebElement>();
		Actions actions = new Actions(driver);
		((JavascriptExecutor) driver).executeScript("var a = document.getElementsByTagName('a'); for(i=0;i<a.length;i++) a[i].setAttribute('target', '_blank');");		
		List<WebElement> links = driver.findElements(By.tagName("a"));
		if(crawlFollowAjax) {
			for (WebElement element : links) {
				try {
					String href = element.getAttribute("href");
					if (href != null) {
						if (!Crawler.urlFilters.execute(href)) continue;
						String text = element.getText();
						if (text == null || text.isEmpty()) continue;
						try {
							System.out.println("No HREF Link :: " + text);
							actions.moveToElement(element).click().build().perform();
							driver.manage().timeouts().implicitlyWait(5, TimeUnit.SECONDS);
						} catch (Exception e) {
							System.out.println("Exception :: " + href);
							continue;
						}
						ArrayList<String> tabs = new ArrayList<String>(driver.getWindowHandles());
						if (tabs.size() > 1) {
							driver.switchTo().window(tabs.get(1));
							driver.close();
							driver.switchTo().window(tabs.get(0));
							linksNormal.add(element);
							System.out.println("Normal Link :: " + href);
						} else {
							linksAjax.add(element);
							System.out.println("JS Link :: " + href);
						}
					} else {
						String text = element.getText();
						if (text != null && !text.isEmpty()) {
							linksAjax.add(element);
							System.out.println("JS Link :: " + element.getText());
						}
					}

				} catch (RuntimeException e) {
					//e.printStackTrace();
					continue;
				}

			}
			List<List<WebElement>> list = new ArrayList<List<WebElement>>();
			list.add(linksNormal);
			list.add(linksAjax);
			return list;
		} else {
			List<List<WebElement>> list = new ArrayList<List<WebElement>>();
			list.add(links);
			list.add(linksAjax);
			return list;			
		}
	}
	
	public static String stripQueryString(String url) {
		int indexOfQues = url.indexOf("?");
		if (indexOfQues != -1) {
			url = url.substring(0, indexOfQues);
		}
		return url;
	}	
	
	public static String stripAnchor(String url) {
		if (url.contains("#")) {
			return url.substring(0, url.indexOf("#"));
		} else {
			return url;
		}
	}
		
}
