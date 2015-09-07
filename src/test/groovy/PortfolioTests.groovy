
//@Grab(group='junit', module='junit', version='4.11')
import org.junit.Test

class PortfolioTests {
    @Test
    void testRiskParity() {
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
}