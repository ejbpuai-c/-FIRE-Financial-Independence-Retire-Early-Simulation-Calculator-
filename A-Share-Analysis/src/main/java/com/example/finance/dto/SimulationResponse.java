package com.example.finance.dto;

import java.util.List;

/**
 * Response payload for the Monte Carlo chart endpoint.
 *
 * Deliberately avoids parallel arrays (e.g. separate labels/p10/p50/p90 lists)
 * since those force the client to re-zip them by index and give no indication
 * of what the numbers mean. Instead: the parameters that produced this result
 * are echoed back, and each year's percentiles are grouped into one record.
 *
 * Example JSON shape:
 * {
 *   "parameters": { "years": 15, "initialCapital": 100000, "simulations": 1000, "mu": 0.07, "sigma": 0.22 },
 *   "currency": "USD",
 *   "series": [
 *     { "year": 0, "p10": 100000, "p50": 100000, "p90": 100000 },
 *     { "year": 1, "p10": 78212,  "p50": 104890, "p90": 139001 }
 *   ]
 * }
 */


public class SimulationResponse {

    private final Parameters parameters;
    private final String currency;
    private final List<YearlyResult> series;
    private final List<HistogramBin> finalValueHistogram;

    public SimulationResponse(Parameters parameters, String currency, List<YearlyResult> series,
                               List<HistogramBin> finalValueHistogram) {
        this.parameters = parameters;
        this.currency = currency;
        this.series = series;
        this.finalValueHistogram = finalValueHistogram;
    }

    public Parameters getParameters() { return parameters; }
    public String getCurrency() { return currency; }
    public List<YearlyResult> getSeries() { return series; }
    public List<HistogramBin> getFinalValueHistogram() { return finalValueHistogram; }

    /** Echoes back the inputs that produced this result, so the response is self-contained. */
    public static class Parameters {
        private final int years;
        private final double initialCapital;
        private final int simulations;
        private final double mu;
        private final double sigma;

        public Parameters(int years, double initialCapital, int simulations, double mu, double sigma) {
            this.years = years;
            this.initialCapital = initialCapital;
            this.simulations = simulations;
            this.mu = mu;
            this.sigma = sigma;
        }

        public int getYears() { return years; }
        public double getInitialCapital() { return initialCapital; }
        public int getSimulations() { return simulations; }
        public double getMu() { return mu; }
        public double getSigma() { return sigma; }
    }

    /** One year's worth of percentile outcomes, grouped together instead of split across arrays. */
    public static class YearlyResult {
        private final int year;
        private final long p10;
        private final long p50;
        private final long p90;

        public YearlyResult(int year, long p10, long p50, long p90) {
            this.year = year;
            this.p10 = p10;
            this.p50 = p50;
            this.p90 = p90;
        }

        public int getYear() { return year; }
        public long getP10() { return p10; }
        public long getP50() { return p50; }
        public long getP90() { return p90; }
    }

    /**
     * One bucket of a histogram over final-year outcomes across all simulated paths.
     * Returned instead of raw per-path values to keep the payload small even at
     * 20,000 simulations, while still giving the client enough to draw a distribution.
     */
    public static class HistogramBin {
        private final long rangeStart;
        private final long rangeEnd;
        private final int count;

        public HistogramBin(long rangeStart, long rangeEnd, int count) {
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.count = count;
        }

        public long getRangeStart() { return rangeStart; }
        public long getRangeEnd() { return rangeEnd; }
        public int getCount() { return count; }
    }
}