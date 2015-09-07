//@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2')
//@Grab(group='au.com.bytecode', module='opencsv', version='2.4')
//@Grab(group='org.apache.commons', module='commons-math3', version='3.5')


import java.net.URL
import java.io.*
import groovy.json.*
import groovy.util.logging.Log
import au.com.bytecode.opencsv.CSVReader
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

/*
Use Yahoo to get monthly returns for a symbol. We simultaneoulsy calculate returns.

http://ichart.finance.yahoo.com/table.csv? s=%5EGSPC - symbol &a=00 - start date month (0-11) &b=1  - start date day
&c=1950 &d=07 &e=1 &f=2009 &g=w -- weekly results, d for daily, and m, for monthly &ignore=.csv  */ 

@Log
public class Stock {
	String symbol    
	double averageRet, variance     
    HashMap annualReturns = [:], monthlyReturns = [:] 
    List dailyReturns = []
    boolean refreshedFromYahoo = false
    
    Stock(String symbol) {
        this.symbol = symbol.toUpperCase()
    }

    boolean loadDataFromDatabase() {
        def storedStock = StockData.get(symbol)
        boolean found = false
        if(storedStock != null) {
            stringToIntervalReturn(storedStock.annual_returns,(char)'a')
            stringToIntervalReturn(storedStock.monthly_returns,(char)'m')
            stringToIntervalReturn(storedStock.daily_returns,(char)'d')
            log.info(String.format("Database Hit: Symbol: %s - Latest entry: %s",symbol, dailyReturns[0]))
            found = true;
        }
        return found;
    }

    boolean saveDataToDataBase() {
        if(refreshedFromYahoo) {
            Date today = new Date();
            def stock = new StockData(
                symbol:      this.symbol,
                annual_returns:  intervalReturnToString(annualReturns),
                monthly_returns:  intervalReturnToString(monthlyReturns),
                daily_returns:  intervalReturnToString(dailyReturns),
                date_saved:   today.format("MM/dd/yyyy")
            )
            stock.save()
            log.info(String.format("Database Store: Symbol %s - Date %s",symbol,today.format("MM/dd/yyyy")))
        }
    }

    void loadData() {
        int refreshDays = 365*15
        if(loadDataFromDatabase()) {
            Date today = new Date()
            if( monthlyReturns.values()[1].getEncoded() 
                == IntervalReturn.getEncoded(today.month+1, today.year) )
                refreshDays = 1250 // Just get the last 1250 claendar days so we can get 1000 closes
            else refreshDays = -1 // We are current
        } 
        if (refreshDays > 0) loadDataFromYahoo(refreshDays)
    }

    void loadDataFromYahoo(days = 365*15) {
        def dataSeries = getHistReturns(days)
        dataSeries[0..0] = [] // remove the header
        Date today = new Date()

        ClosingPrice monthEnd, yearEnd, dayEnd
        def gain = { ClosingPrice past,ClosingPrice now ->
            double r =((now.closingPrice-past.closingPrice)/past.closingPrice)
            return Math.round(r*100000)/100000
        }
        // The data series is returned is reverse chronological order
        dailyReturns = [] // clear old data now that we have newer results
        for(data in dataSeries) {
            if(data.size() != 7) {
                log.error("Parsing failed while retrieving data for %s. There should have been 7 elements.")
                throw new Exception("There should be 7 elements in the list.")
            }
            ClosingPrice prevDay = new ClosingPrice(Date.parse('yyyy-MM-dd',data[0]),Double.parseDouble(data[6]))
            
            if(dayEnd != null ) {
                if( dayEnd.date[Calendar.YEAR] != today[Calendar.YEAR] ||
                    dayEnd.date[Calendar.MONTH] != today[Calendar.MONTH] ) {
                    def dr = new IntervalReturn(dayEnd.date,gain(prevDay, dayEnd))
                    dailyReturns.add(dr)
                }
                if (dayEnd.date[Calendar.YEAR] != prevDay.date[Calendar.YEAR]) {
                    if (yearEnd != null) {
                        // Subtract one from year because the return measured in Jan 2015 is for the year 2014
                        def ar = new IntervalReturn(yearEnd.date.year-1+1900,gain(dayEnd, yearEnd)*100)
                        annualReturns[ar.getEncoded()] = ar
                    }
                    yearEnd = dayEnd
                }
                if (dayEnd.date[Calendar.MONTH] != prevDay.date[Calendar.MONTH]) {
                    if (monthEnd != null) {
                        // Subtract fifteen days to get the previous month to which the return belongs
                        // The first day of the new month is no necessarily 1st because there might be a weekend or holiday
                        Date returnMonth = monthEnd.date.minus(15)
                        def mr = new IntervalReturn(returnMonth.month+1,returnMonth.year+1900,gain(dayEnd, monthEnd)*100)
                        monthlyReturns[mr.getEncoded()] = mr
                    }
                    monthEnd = dayEnd
                }
            }
            dayEnd = prevDay
        }
        refreshedFromYahoo = true
    }

    String intervalReturnToString(returns) {
        def json = new JsonBuilder()
        def list = []
        if (returns instanceof HashMap)
            list = returns.collect { k, v -> [k, v.theReturn] }
        else if (returns instanceof List)
            list = returns.collect { [it.getEncoded(), it.theReturn] }
        def str = json(list)
        return str
    }

    def stringToIntervalReturn(String jsonText, char type) {
        def json = new JsonSlurper()
        def list = json.parseText(jsonText)
        def intReturns
        if (type == 'd')
            intReturns = list.collect { new IntervalReturn(it[0],it[1],type) }
        else if (type == 'a' || type == 'm')
            intReturns = list.collectEntries { [it[0],new IntervalReturn(it[0],it[1],type)] }
        if(type == 'd') this.dailyReturns = intReturns
        if(type == 'm') this.monthlyReturns = intReturns
        if(type == 'a') this.annualReturns = intReturns
    }

    void computeStats() {
        double[] areturns = annualReturns.collect { k,v -> (v.theReturn/100) }
        if(areturns.size() > 0) {
            DescriptiveStatistics desc = new DescriptiveStatistics(areturns)
            this.variance = desc.getStandardDeviation()
            this.averageRet = desc.getMean()
        }
    }

    String toString() {
        StringBuilder bld = new StringBuilder()
        bld.append(String.format("Stock: %s\n",symbol))
        bld.append("\nAnnual Returns\n")
        annualReturns.each { k, v -> bld.append(v.toString()+"\n") }
        bld.append(String.format("\nLast 10 Monthly Returns (%s) available\n",monthlyReturns.size()))
        monthlyReturns.take(10).each { k, v -> bld.append(v.toString()+"\n") }
        bld.append(String.format("\nLast 10 Daily Returns (%s) available\n",dailyReturns.size()))
        dailyReturns.take(10).each { k, v -> bld.append(v.toString()+"\n") }
        bld.append("\nStats\n")
        bld.append(sprintf("Variance    : %8.2f %s\n",this.variance*100,"%"))
        bld.append(sprintf("Avg. Return : %8.2f %s\n",this.averageRet*100,"%"))
        return bld.toString()
    }

    ArrayList getHistReturns(int days) {   
        def IntervalName = ['d':'daily', 'w':'weekly', 'm':'monthly']
        String interval = 'd'    
        Date today = new Date();
        Date end = today.minus(days)
        def data
        String urlString = String.format("?s=%s&a=%d&b=%d&c=%d&d=%d&e=%d&f=%d&g=%s&ignore=.csv",symbol,end[Calendar.MONTH],
                                        end[Calendar.DAY_OF_MONTH],end[Calendar.YEAR],
                                        today[Calendar.MONTH],today[Calendar.DAY_OF_MONTH],today[Calendar.YEAR],interval)
        log.info(String.format("Getting url with parameters %s",urlString))
        URL y = new URL(String.format("http://ichart.finance.yahoo.com/table.csv"+urlString))
        BufferedReader reader = new BufferedReader(new InputStreamReader(y.openStream()));
        CSVReader cr = new CSVReader(reader)
        data = cr.readAll()
        return data
    }
}

class ClosingPrice {
    Date date
    double closingPrice

    ClosingPrice(Date date, double closingPrice) {
        this.date = date
        this.closingPrice = closingPrice
    }
}
 