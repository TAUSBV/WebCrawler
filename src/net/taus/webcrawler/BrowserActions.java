package net.taus.webcrawler;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

public class BrowserActions {
	
	public static void scrollDown(WebDriver driver) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("window.scrollBy(0, 1000);");
	}
	
	public static void scrollUp(WebDriver driver) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		js.executeScript("window.scrollBy(0, -1000);");
	}	
	
	public static void scrollToBottom(WebDriver driver) {
		boolean more = true;
		do {
			System.out.println("scrolling more ...");
			scrollDown(driver);
			driver.manage().timeouts().implicitlyWait(5,TimeUnit.SECONDS) ;
			more = moreToScroll(driver);
		} while(!more);		
	}
	
	public static boolean moreToScroll(WebDriver driver) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		return (boolean)js.executeScript("if (document.body.scrollHeight == document.documentElement.scrollTop + window.innerHeight) { return true; } else { return false; }");		
	}

}
