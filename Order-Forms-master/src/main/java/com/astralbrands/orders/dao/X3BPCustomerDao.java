package com.astralbrands.orders.dao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

//import javax.activation.DataSource;
//import javax.sql.DataSource;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

@Repository
public class X3BPCustomerDao {

	private String netId = "NET30";

	@Autowired
	@Qualifier("x3DataSource")
	DataSource x3DataSource;

	private Connection connection;

	public String getPaymentTerms(String customerId) {
		System.out.println("Customer id is: " + customerId);
		if (customerId == null) {
			System.out.println();
			return netId;
		}
		if (x3DataSource != null) {
			try {
				if (connection == null) {
					connection = x3DataSource.getConnection();
					connection.setAutoCommit(true);
				}
				try (Statement statement = connection.createStatement();) {
					String query = "select PTE_0 from PROD.BPCUSTOMER where BPCNUM_0 ='" + customerId + "'";
					ResultSet paymentTerm = statement.executeQuery(query);
					while (paymentTerm.next()) {
						netId = paymentTerm.getString("PTE_0");
					}
					System.out.println("Payment term for " + customerId + "is: " + netId);
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println(" Exception " + e.getMessage());
				}

			} catch (SQLException ex) {
				ex.printStackTrace();
				System.err.println(ex.getMessage());
			}

		} else {
			System.err.println("Bit boot data source is null");
		}

		return netId;
	}
}
