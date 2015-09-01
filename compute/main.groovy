//def y = new Portfolio(["VTI","VWO"],null)
Stock s = new Stock("VTI")
s.loadData()
def a = s.IntervalReturnToString(s.annualReturns)
def m = s.IntervalReturnToString(s.monthlyReturns)
def d = s.IntervalReturnToString(s.dailyReturns)
println "Size:" + (a.size() + m.size() + d.size())

Stock s2 = new Stock("VTI")
//s2.StringToIntervalReturn(a,(char)'a')
//s2.StringToIntervalReturn(m,(char)'m')
s2.StringToIntervalReturn(d,(char)'d')
print s2

//s = new Stock("VTI")
//s.loadData()
//print s


//y.loadStats(1000)
//y.testRiskParity()