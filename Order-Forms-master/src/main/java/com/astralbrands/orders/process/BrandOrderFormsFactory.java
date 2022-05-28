package com.astralbrands.orders.process;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/*
	This class provides a mechanism for the Router
	to choose the correct Processor for the file being uploaded
	Single function that compares the site(last substring) in
	the file's name and returns the proper Processor Object
*/
@Component
public class BrandOrderFormsFactory {
	
	@Autowired
	private AloutteOrderProcessor aloutteOrderProcessor;
	
	@Autowired
	private CosmedixOrderProcessor cosmedixOrderProcessor;
	
	@Autowired
	private CommerceHubProcessor commerceHubProcessor;
	

	public BrandOrderForms getBrandOrderForms(String site) {
		if ("COS".equals(site)) {
			return cosmedixOrderProcessor;
		}
		else if("HUB".equalsIgnoreCase(site)) {
			return commerceHubProcessor;
		}
		return aloutteOrderProcessor;
	}

}
