package com.example.finance.service;

import com.example.finance.dto.SimulationResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulates portfolio value paths using Geometric Brownian Motion (GBM):
 *
 *   S(t) = S(t-1) * exp((mu - sigma^2 / 2) + sigma * Z)
 *
 * where Z is a standard normal random variable. This models compounding
 * returns with random annual shocks around a drift (mu) and volatility (sigma).
 */
@Service
public class MonteCarloSimulationService {

    /**
     * Runs the simulation and returns the 10th/50th/90th percentile trajectories,
     * one record per year from 0 to {@code years} inclusive, with the input
     * parameters echoed back so the response is self-contained.
     *
     * @param years          projection horizon in years
     * @param initialCapital starting portfolio value
     * @param simulations    number of simulated paths
     * @param mu             expected annual return (e.g. 0.07 for 7%)
     * @param sigma          annual volatility (e.g. 0.22 for 22%)
     */
    public SimulationResponse run(int years, double initialCapital, int simulations,
                                   double mu, double sigma) {

        // yearlyResults[t][i] = simulated portfolio value at year t for simulation path i
        double[][] yearlyResults = new double[years + 1][simulations];

        for (int i = 0; i < simulations; i++) {
            yearlyResults[0][i] = initialCapital;
        }

        // ThreadLocalRandom avoids contention if this service is invoked concurrently
        // by multiple requests, unlike a single shared java.util.Random instance.
        for (int i = 0; i < simulations; i++) {
            double current = initialCapital;
            for (int t = 1; t <= years; t++) {
                double z = ThreadLocalRandom.current().nextGaussian();
                current = current * Math.exp((mu - 0.5 * sigma * sigma) + sigma * z);
                yearlyResults[t][i] = current;
            }
        }

        List<SimulationResponse.YearlyResult> series = new ArrayList<>();
        for (int t = 0; t <= years; t++) {
            Arrays.sort(yearlyResults[t]);
            series.add(new SimulationResponse.YearlyResult(
                    t,
                    percentile(yearlyResults[t], 10),
                    percentile(yearlyResults[t], 50),
                    percentile(yearlyResults[t], 90)
            ));
        }

        // The final year's array is already sorted from the loop above, so build the
        // distribution histogram from it directly rather than re-sorting.
        List<SimulationResponse.HistogramBin> finalValueHistogram =
                buildHistogram(yearlyResults[years], 20);

        SimulationResponse.Parameters parameters =
                new SimulationResponse.Parameters(years, initialCapital, simulations, mu, sigma);

        return new SimulationResponse(parameters, "USD", series, finalValueHistogram);
    }

    /**
     * Buckets a sorted array of final-year outcomes into a fixed number of equal-width
     * bins spanning [min, max]. Returned as counts per bin rather than raw values, so
     * the payload stays small (a fixed ~20 numbers) no matter how many paths were
     * simulated, while still letting the client render a distribution shape.
     */
    private List<SimulationResponse.HistogramBin> buildHistogram(double[] sortedFinalValues, int binCount) {
        List<SimulationResponse.HistogramBin> bins = new ArrayList<>();
        double min = sortedFinalValues[0];
        double max = sortedFinalValues[sortedFinalValues.length - 1];

        // Guard against a degenerate zero-width range (e.g. sigma so small every
        // path lands on the same value), which would otherwise divide by zero.
        if (max <= min) {
            bins.add(new SimulationResponse.HistogramBin(Math.round(min), Math.round(min), sortedFinalValues.length));
            return bins;
        }

        double binWidth = (max - min) / binCount;
        int[] counts = new int[binCount];
        for (double value : sortedFinalValues) {
            int binIndex = (int) ((value - min) / binWidth);
            if (binIndex >= binCount) binIndex = binCount - 1; // the max value lands exactly on the boundary
            counts[binIndex]++;
        }

        for (int b = 0; b < binCount; b++) {
            long rangeStart = Math.round(min + b * binWidth);
            long rangeEnd = Math.round(min + (b + 1) * binWidth);
            bins.add(new SimulationResponse.HistogramBin(rangeStart, rangeEnd, counts[b]));
        }
        return bins;
    }

    /**
     * Computes the given percentile from a pre-sorted array using linear interpolation
     * between the two nearest ranks. This is more accurate than a fixed index offset
     * (e.g. array[100] for "10th percentile of 1000") and stays correct regardless of
     * how many simulations the caller requests.
     */
    private long percentile(double[] sortedData, double percentile) {
        double rank = (percentile / 100.0) * (sortedData.length - 1);
        int lowerIndex = (int) Math.floor(rank);
        int upperIndex = (int) Math.ceil(rank);
        if (lowerIndex == upperIndex) {
            return Math.round(sortedData[lowerIndex]);
        }
        double weight = rank - lowerIndex;
        double interpolated = sortedData[lowerIndex] * (1 - weight) + sortedData[upperIndex] * weight;
        return Math.round(interpolated);
    }
}