package com.astralbrands.orders.process;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.astralbrands.orders.constants.AppConstants;
/*
	This Processor class reads the Excel file name
	If file name is not properly formatted exception thrown
	Based on the site(last substring before the ".extension")
	it will use the correct Processor(company) class to continue
 */
@Component
public class ReadXslxFileProcessor implements Processor, AppConstants {

	/*
		Object that is used to determine the correct Processor
		for the current file
	 */
	@Autowired
	private BrandOrderFormsFactory orderFormsFactory;

	Logger log = LoggerFactory.getLogger(ReadXslxFileProcessor.class);

	@Override
	public void process(Exchange exchange) throws Exception {
		
		System.out.println("Exchange value is "+exchange);

		String fileName = exchange.getIn().getHeader("CamelFileName", String.class);
		if (fileName == null) {
			exchange.setProperty(IS_DATA_PRESENT, false);
			return;
		}
		log.info("input file name: " + fileName);
		String[] fileNameData = fileName.split(UNDERSCORE);
		if (fileNameData.length < 3) {
			log.error("Invalid file name :" + fileName + ", File name should be customerId_date_site.xls");
			throw new RuntimeException("Invalid File name");
		}
		exchange.setProperty(INPUT_FILE_NAME, removeExtention(fileName));
		System.out.println("Exchange name is "+exchange);
		String NfileName = removeExtention(fileName);
		String[] currentFileNameData = NfileName.split(UNDERSCORE);
		for(int i=0; i<fileNameData.length;i++) {
			System.out.println("File data is : "+currentFileNameData[i]);
		}
		String ext = fileName.substring(fileName.indexOf(DOT) + 1, fileName.lastIndexOf("x") + 1);
		System.out.println("File extension is : " + ext);
		String site = fileName.substring(fileName.lastIndexOf(UNDERSCORE) + 1, fileName.indexOf(DOT)); // Retrieves site from file name
		System.out.println("Site name is: "+site);
		BrandOrderForms orderFormProcessor = orderFormsFactory.getBrandOrderForms(site); // This is where the program chooses the correct Processor class
		orderFormProcessor.process(exchange, site, fileNameData); // Executes the correct Processor
	}

	// Removes the extension from the file's name and returns everything before the '.'
	private String removeExtention(String fileName) {
		return fileName.substring(0, fileName.indexOf(DOT));
	}

}
