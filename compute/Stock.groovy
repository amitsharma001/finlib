@Grab(group='org.apache.logging.log4j', module='log4j-core', version='2.3')
@Grab(group='org.apache.logging.log4j', module='log4j-api', version='2.3')
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2')
@Grab(group='org.apache.commons', module='commons-math3', version='3.5')
@Grab(group='au.com.bytecode', module='opencsv', version='2.4')


import groovyx.net.http.HTTPBuilder
import groovy.transform.*
import static groovyx.net.http.ContentType.TEXT
import org.apache.logging.log4j.*  
import au.com.bytecode.opencsv.CSVReader
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation 
import org.apache.commons.math3.linear.RealMatrix

/**
 * Created by Amit on 7/18/2014.
 */
class ClosingPrice {
    Date date
    double closingPrice
    double dailyReturn
    double annualReturn

    ClosingPrice(Date date, double closingPrice) {
        this.date = date
        this.closingPrice = closingPrice
    }
    String toString() {
        return String.format("Date: %s, Price: %s, Return: %.4f",date.format("MM/dd/yy"),closingPrice,dailyReturn)
    }
}

/*
Use Yahoo to get monthly returns for a symbol. We simultaneoulsy calculate returns.

http://ichart.finance.yahoo.com/table.csv?
s=%5EGSPC - symbol
&a=00 - start date month (0-11)
&b=1  - start date day
&c=1950
&d=07
&e=1
&f=2009
&g=w -- weekly results, d for daily, and m, for monthly
&ignore=.csv
 */
class StockData {
    def IntervalName = ['d':'daily', 'w':'weekly', 'm':'monthly']
    String symbol
    String interval = 'd'
    double averageRet, variance
    def dailyCloses = [], annualCloses = []
    HashMap annualReturns = null
    Logger log = LogManager.getLogger("com.betasmart") 
    
    StockData(symbol) {
        this.symbol = symbol
    }

    def loadData(days = 365*4) {
        def dataSeries = getHistReturns(days)
        dataSeries[0..0] = [] // remove the header

        ClosingPrice monthEnd, yearEnd, dayEnd
        def gain = {ClosingPrice past,ClosingPrice now ->(now.closingPrice-past.closingPrice)/past.closingPrice}

        for(data in dataSeries) {
            if(data.size() != 7) throw new Exception("There should be 7 elements in the list.")
            ClosingPrice prevDay = new ClosingPrice(Date.parse('yyyy-MM-dd',data[0]),Double.parseDouble(data[6]))
            dailyCloses.add(prevDay)
            if(dayEnd != null ) {
                dayEnd.dailyReturn = gain(prevDay, dayEnd)
                if (dayEnd.date[Calendar.YEAR] != prevDay.date[Calendar.YEAR]) {
                    if (yearEnd != null) {
                        yearEnd.annualReturn = gain(dayEnd, yearEnd)
                        annualCloses.add(yearEnd)
                    }
                    yearEnd = dayEnd
                }
            }
            dayEnd = prevDay
        }
        annualReturns = annualCloses.collectEntries {
            [it.date.year-1+1900,it.annualReturn*100]
        }
        double[] returns = dailyCloses.collect { it.dailyReturn }
        DescriptiveStatistics desc = new DescriptiveStatistics(returns)
        this.averageRet = desc.getMean()
        this.variance = desc.getStandardDeviation()
    }

    String toString() {
        StringBuilder bld = new StringBuilder()
        bld.append(String.format("Stock: %s\r\n",symbol))
        annualReturns.each { k, v ->
            printf "%-10d: %8.2f\n",k,v
        }
        return bld.toString()
    }

    def getHistReturns(int days) {
        URL y = new URL("http://ichart.finance.yahoo.com/table.csv")
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

@CompileStatic
class MyMatrix {
    RealMatrix instance = null;
    MyMatrix(RealMatrix x) {
        instance = x
    }

    public double[][] getData() {
        instance.getData()
    }
}

class Portfolio {
    def stocks = []
    double[][] correlation = null
    Date startDate, endDate
    def weights = [], returns = []
    int numberOfDays = 3650
    String dataLimitingSymbol
    Logger log = LogManager.getLogger("com.betasmart") 

    Portfolio(symbols, weights) {
        this.weights = weights
        int numberOfCloses = numberOfDays
        if (weights != null && symbols.size() != weights.size())
            throw new Exception("The number of weights should match the number of symbols.")
        for (stock in symbols) {
            def s = new StockData(stock)
            s.loadData(numberOfDays)
            log.info(String.format("Acquired data for Symbol %s from %s to %s",stock,s.dailyCloses[0].date.format("MM/dd/yy"),s.dailyCloses[-1].date.format("MM/dd/yy")))
            if(s.dailyCloses.size() < numberOfCloses) {
                numberOfCloses = s.dailyCloses.size()
                dataLimitingSymbol = s.symbol
            }
            stocks.add(s)
            print s
        }
        log.info(String.format("Usable data %d days because of symbol %s",numberOfDays,dataLimitingSymbol))
        int k = 0
        for(StockData s in stocks) {
            def useData = s.dailyCloses[0..(numberOfCloses-1)]
            if(startDate == null) {
                startDate = useData[-1].date
                endDate = useData[0].date
            } else if(startDate != useData[-1].date || endDate != useData[0].date) {
                throw Exception("The return arrays don't match across symbols.")
            }
            //double[] data = useData.collect { it.dailyReturn }
            returns.add(useData)
        }
        returns = returns.transpose()
        double[][] cov = returns.collectNested { it.dailyReturn }

        PearsonsCorrelation c = new PearsonsCorrelation(cov)
        correlation = new MyMatrix(c.getCorrelationMatrix()).getData()
        print correlation
        //println correlationMatrix.metaClass.methods*.name.sort().unique() 
        //correlation = c.getCorrelationMatrix().getData()
        //print correlation
    }

    String toString() {
        return String.format("LimitingStock: %s, StartDate: %s, EndDate: %s",this.dataLimitingSymbol,startDate.format("MM/dd/yy"),endDate.format("MM/dd/yy"))
    }
}


def Y = new Portfolio(["VTI","VWO","VEA","VNQ"],null)
print(Y)