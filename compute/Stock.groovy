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
import org.apache.commons.math3.stat.correlation.Covariance
import org.apache.commons.math3.linear.LUDecomposition
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.MatrixUtils

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

    def loadData(days = 365*11) {
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
        double[] dreturns = dailyCloses.collect { it.dailyReturn }
        double[] areturns = annualCloses.collect { it.annualReturn }
        DescriptiveStatistics desc = new DescriptiveStatistics(areturns)
        this.variance = desc.getStandardDeviation()
        this.averageRet = desc.getMean()
    }

    String toString() {
        StringBuilder bld = new StringBuilder()
        bld.append(String.format("Stock: %s\n",symbol))
        annualReturns.each { k, v ->
            bld.append(sprintf("%-12d: %8.2f %s\n",k,v,"%"))
        }
        bld.append(sprintf("Variance    : %8.2f %s\n",this.variance*100,"%"))
        bld.append(sprintf("Avg. Return : %8.2f %s\n",this.averageRet*100,"%"))
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
    def symbols = null
    double[][] correlation = null
    Date startDate, endDate
    def weights = [], returns = []
    String dataLimitingSymbol
    Logger log = LogManager.getLogger("com.betasmart") 

    Portfolio(def symbols, def weights=null) {
        this.symbols = symbols
        this.weights = weights
        if(weights != null) {
            if(symbols.size() != weights.size()) 
                throw new Exception(String.format("The number of symbols (%d) should match the number of weights (%d).",symbols.size(),weights.size()))
            if(weights.sum() != 1)
                throw new Exception("The weights of the portfolio should add to 1.")
        }
    }

    def loadStats(int numberOfDays=3650) {
        int numberOfCloses = numberOfDays
        for (stock in symbols) {
            def s = new StockData(stock)
            s.loadData(numberOfDays)
            log.info(String.format("Acquired data for Symbol %s from %s to %s",stock,s.dailyCloses[0].date.format("MM/dd/yy"),s.dailyCloses[-1].date.format("MM/dd/yy")))
            if(s.dailyCloses.size() < numberOfCloses) {
                numberOfCloses = s.dailyCloses.size()
                dataLimitingSymbol = s.symbol
            }
            print(s)
            stocks.add(s)
        }
        log.info(String.format("Usable data %d closes because of symbol %s",numberOfCloses,dataLimitingSymbol))
        int k = 0
        for(StockData s in stocks) {
            def useData = s.dailyCloses[0..(numberOfCloses-1)]
            if(startDate == null) {
                startDate = useData[-1].date
                endDate = useData[0].date
            } else if(startDate != useData[-1].date || endDate != useData[0].date) {
                throw Exception("The return arrays don't match across symbols.")
            }
            returns.add(useData)
        }
        returns = returns.transpose()
        double[][] cov = returns.collectNested { it.dailyReturn }   
        PearsonsCorrelation c = new PearsonsCorrelation(cov)
        correlation = new MyMatrix(c.getCorrelationMatrix()).getData()
        print correlation
    }


    //Compute Risk Parity Portfolio using Newton's method of estimation
    def compute_risk_parity_portfolio(double[][] corr) {
        if( corr == null) return
        int numStock = corr[0].length
        '''initial guess - equal weighted and a random lambda'''
        //lam = int(random.random()*100)/100
        double lamda = 0.1
        double equiw = 1.0/numStock
        log.info(String.format("Initial Guess Lambda: %.2f Weight: %.2f",lamda,equiw))
        RealMatrix cov = MatrixUtils.createRealMatrix(corr)
        def xw = []
        numStock.times { xw.add(equiw) }
        while(true) {
            def xwv = MatrixUtils.createRealVector(xw as double[])
            def F_x_cov = cov.operate(xwv)
            def lamdax = MatrixUtils.createRealVector(xw.collect { lamda/it } as double[])
            def F_x_top = F_x_cov.subtract(lamdax)
            double F_x_bot = 0
            xw.each { F_x_bot += it }
            F_x_bot -= 1
            def F_x_nList = F_x_top.toArray().toList()
            F_x_nList.add(F_x_bot)
            def F_x_n = MatrixUtils.createRealVector(F_x_nList as double[])
            def diag = xw.collect { lamda/(it*it) }
            def J_top_left_lam = MatrixUtils.createRealDiagonalMatrix(diag as double[])
            def J_top_left = cov.add(J_top_left_lam)
            def negx = xwv.toArray().collect { -1/(it) }
            negx.add(0)
            def J = []
            J_top_left.getData().eachWithIndex { arr, idx -> 
                def x = arr.toList(); 
                x.add(negx[idx]); 
                J.add(x)
            }
            def ones = []
            numStock.times { ones.add(1) }
            ones.add(0)
            J.add(ones)
            def Yn = xw.collect{ it }
            Yn.add(lamda)
            def Ynm = MatrixUtils.createRealVector(Yn as double[])
            RealMatrix Jm = MatrixUtils.createRealMatrix(J as double[][])
            def JInverse = new LUDecomposition(Jm).getSolver().getInverse()
            def yn1 = Ynm.subtract(JInverse.operate(F_x_n))
            def yn1l = yn1.toArray()
            lamda = yn1l[-1]
            def error = xw
            xw = yn1l[0..-2]
            error.eachWithIndex{ weight, index -> error[index] = weight - xw[index]}
            log.info(String.format("Error: %s",error))
            boolean allzero = true
            error.each { if(it.round(10) != 0) allzero = false;}
            if(allzero) break;
        }
        return xw
    }

    def testRiskParity() {
        def p = new Portfolio(["WFM","MSFT","GOOG","GE"])
        def vr =  [ 
            [0.01,  0   , 0.015,         0, 0.02], 
            [    0, 0.04,  0.03,         0, 0.02],
            [0.015, 0.03,  0.09,         0, 0.02],
            [    0, 0   ,     0,    0.2025, 0.01],
            [    0, 0   ,     0,    0.2025, 0.01]
        ] as double[][]
        def x = p.compute_risk_parity_portfolio(vr)
        print x
        assert (x[0].round(7)) == (0.3999205774039974d.round(7))
        assert (x[1].round(7)) == (0.2216712974614022d.round(7))
        assert (x[2].round(7)) == (0.12669280715894068d.round(7))
        assert (x[3].round(7)) == (0.12585765898782988d.round(7))
        assert (x[4].round(7)) == (0.12585765898782988d.round(7))
        
    }

    String toString() {
        return String.format("LimitingStock: %s, StartDate: %s, EndDate: %s",this.dataLimitingSymbol,startDate.format("MM/dd/yy"),endDate.format("MM/dd/yy"))
    }
}


def y = new Portfolio(["VTI","VWO"],null)
StockData s = new StockData("FB")
s.loadData()
print s
//y.loadStats(1000)
//y.testRiskParity()
 