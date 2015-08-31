USE stocks;

DROP TABLE IF EXISTS stock_return;
DROP TABLE IF EXISTS stock_correlation;
DROP TABLE IF EXISTS stock;

CREATE TABLE IF NOT EXISTS stock (
	symbol VARCHAR(10), 
	PRIMARY KEY (symbol)
	);

CREATE TABLE IF NOT EXISTS stock_annual_return (
	symbol VARCHAR(10), 
	year int, 
	annual_return double, 
	PRIMARY KEY (symbol,year), 
	FOREIGN KEY FK_AR_STOCK (symbol) REFERENCES stock(symbol)
	);

CREATE TABLE IF NOT EXISTS stock_daily_return (
	symbol VARCHAR(10),
	returns VARCHAR(,
	computed date,
	PRIMARY KEY (symbol1,symbol2), 
	FOREIGN KEY FK_SC_STOCK1 (symbol1) REFERENCES stock(symbol),
	FOREIGN KEY FK_SC_STOCK2 (symbol2) REFERENCES stock(symbol)
	);

