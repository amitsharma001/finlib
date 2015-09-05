@Grab(group='org.apache.commons', module='commons-math3', version='3.5')

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.stat.correlation.*
import org.apache.commons.math3.linear.*
import groovy.util.logging.Log
import groovy.transform.*


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

@Log
class Portfolio {
    def stocks = []
    def symbols = null
    double[][] correlation = null
    Date startDate, endDate
    def weights = [], returns = []
    String dataLimitingSymbol

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

    def loadStats() {
        int numberOfCloses = 3000
        for (stock in symbols) {
            def s = new Stock(stock)
            s.loadData()
            log.info(String.format("Acquired data for Symbol %s from %s to %s",stock,s.dailyReturnsL[0].date.format("MM/dd/yy"),s.dailyReturnsL[-1].date.format("MM/dd/yy")))
            if(s.dailyReturnsL.size() < numberOfCloses) {
                numberOfCloses = s.dailyReturnsL.size()
                dataLimitingSymbol = s.symbol
            }
            print(s)
            stocks.add(s)
        }
        log.info(String.format("Usable data %d closes because of symbol %s",numberOfCloses,dataLimitingSymbol))
        int k = 0
        for(Stock s in stocks) {
            def useData = s.dailyReturnsL[0..(numberOfCloses-1)]
            if(startDate == null) {
                startDate = useData[-1].date
                endDate = useData[0].date
            } else if(startDate != useData[-1].date || endDate != useData[0].date) {
                throw Exception("The return arrays don't match across symbols.")
            }
            returns.add(useData)
        }
        returns = returns.transpose()
        double[][] cov = returns.collectNested { it.theReturn }   
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