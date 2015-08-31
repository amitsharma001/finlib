//def y = new Portfolio(["VTI","VWO"],null)
Stock s = new Stock("VTI")
s.loadData()
def a = s.IntervalReturnToString(s.annualReturns)
def m = s.IntervalReturnToString(s.monthlyReturns)
def d = s.IntervalReturnToString(s.dailyReturns)
println "Size:" + a.size() + m.size() + d.size()
print s


//s = new Stock("VTI")
//s.loadData()
//print s


//y.loadStats(1000)
//y.testRiskParity()