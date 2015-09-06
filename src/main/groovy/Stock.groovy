//@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2')
//@Grab(group='au.com.bytecode', module='opencsv', version='2.4')
//@Grab(group='org.apache.commons', module='commons-math3', version='3.5')


import groovyx.net.http.HTTPBuilder
import groovy.json.*
import static groovyx.net.http.ContentType.TEXT
import groovy.util.logging.Log
import au.com.bytecode.opencsv.CSVReader
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

/**
 * Created by Amit on 7/18/2014.
 */
class ClosingPrice {
    Date date
    double closingPrice

    ClosingPrice(Date date, double closingPrice) {
        this.date = date
        this.closingPrice = closingPrice
    }
}

class IntervalReturn {
    public static Date offset = Date.parse("yyyy-MM-dd hh:mm:ss", "2000-01-01 00:00:00")
    int year = -1, month = -1
    Date date = null
    double theReturn

    IntervalReturn(Date date, double dreturn) {
        this.date = date
        this.theReturn = dreturn
    }
    IntervalReturn( int month, int year, double mreturn) {
        this.year = year
        this.month = month 
        this.theReturn = mreturn
    }
    IntervalReturn(int year, double areturn) {
        this.year = year
        this.theReturn = areturn
    }
    IntervalReturn(int encodedDate, double treturn, char type) {
        this.theReturn = treturn
        setDecoded(encodedDate, type)
    }
    void setDecoded(int encodedDate, char type) {
        if(type == 'a') this.year = encodedDate + 2000
        if(type == 'm') {
            year = (int)(encodedDate / 12) + 2000
            month = encodedDate % 12
            if(month <= 0) {
                year -= 1
                month += 12
            }
        } 
        if(type == 'd') {
            this.date = IntervalReturn.offset.plus (encodedDate)
        }        
    }
    int getEncoded() {
        if(date != null) return date.minus(IntervalReturn.offset)
        if(month != -1) return (year - 2000)*12 + month
        else if(year != -1) return year - 2000
    }
    String toString() {
        if(date != null) return String.format("Date: %s, Days since 2000: %d, Return: %.4f",date.format("MM/dd/yy"),getEncoded(),theReturn)
        if(month != -1) return String.format("%d %2d/%-12d: %8.2f %s",getEncoded(),month,year,theReturn,"%")
        if(year != -1) return String.format("%-12d: %8.2f %s",year,theReturn,"%")
        return "No date associated with return"
    }
}

/*
Use Yahoo to get monthly returns for a symbol. We simultaneoulsy calculate returns.

http://ichart.finance.yahoo.com/table.csv? s=%5EGSPC - symbol &a=00 - start date month (0-11) &b=1  - start date day
&c=1950 &d=07 &e=1 &f=2009 &g=w -- weekly results, d for daily, and m, for monthly &ignore=.csv  */ 
@Log
public class Stock {
	String symbol    
	double averageRet, variance     
    HashMap annualReturns = [:], monthlyReturns = [:], dailyReturns = [:]
    List dailyReturnsL = []
    
    Stock(symbol) {
        this.symbol = symbol
    }

    void loadData(days = 365*15) {
        def dataSeries = getHistReturns(days)
        dataSeries[0..0] = [] // remove the header
        Date today = new Date();

        ClosingPrice monthEnd, yearEnd, dayEnd
        def gain = {ClosingPrice past,ClosingPrice now ->((now.closingPrice-past.closingPrice)/past.closingPrice).round(5)}
        // The data series is returned is reverse chronological order
        for(data in dataSeries) {
            if(data.size() != 7) throw new Exception("There should be 7 elements in the list.")
            ClosingPrice prevDay = new ClosingPrice(Date.parse('yyyy-MM-dd',data[0]),Double.parseDouble(data[6]))
            
            if(dayEnd != null ) {
                if( dayEnd.date[Calendar.YEAR] != today[Calendar.YEAR] ||
                    dayEnd.date[Calendar.MONTH] != today[Calendar.MONTH] ) {
                    def dr = new IntervalReturn(dayEnd.date,gain(prevDay, dayEnd))
                    dailyReturns[dr.getEncoded()] = dr
                    dailyReturnsL.add(dr)
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
    }

    String IntervalReturnToString(returns) {
        def json = new JsonBuilder()
        def list = returns.take(1000).collect { k, v ->
            [k, v.theReturn]
        }
        def str = json(list)
        return str
    }

    def StringToIntervalReturn(String jsonText, char type) {
        def json = new JsonSlurper()
        def list = json.parseText(jsonText)
        def intReturns = list.collectEntries {
            [it[0],new IntervalReturn(it[0],it[1],type)]
        }
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
        URL y = new URL("http://ichart.finance.yahoo.com/table.csv")
        def IntervalName = ['d':'daily', 'w':'weekly', 'm':'monthly']
        String interval = 'd'    
        Date today = new Date();
        Date end = today.minus(days)
        def data
        def http = new HTTPBuilder("http://ichart.finance.yahoo.com")
        log.info(String.format("Getting %s returns for the %s symbol going back %s days.",IntervalName[interval],symbol,days))
        http.get( path: "/table.csv", contentType: TEXT, query:['s':symbol,'a':end[Calendar.MONTH],'b':end[Calendar.DAY_OF_MONTH],'c':end[Calendar.YEAR]
                             ,'d':today[Calendar.MONTH],'e':today[Calendar.DAY_OF_MONTH],'f':today[Calendar.YEAR]
                             ,'g':interval,'ignore':'.csv']) { resp, reader ->
                CSVReader cr = new CSVReader(reader)
                data = cr.readAll()
            }
        return data
    }
}
 