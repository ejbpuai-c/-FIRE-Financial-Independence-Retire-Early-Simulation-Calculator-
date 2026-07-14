package com.example.finance.controller;

import com.example.finance.dto.SimulationResponse;
import com.example.finance.service.MonteCarloSimulationService;
import org.springframework.web.bind.annotation.CrossOrigin;
//import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes Monte Carlo portfolio projection data for charting on the front end.
 * All simulation inputs are caller-supplied query parameters, each validated
 * against a safe range so a request can't hang or crash the server
 * (e.g. an unbounded number of simulations or an absurd time horizon).
 */
@RestController
@CrossOrigin(origins = {
        "https://yourapp.example.com", // replace with your real production origin
        "http://localhost:63342"       // IntelliJ/WebStorm built-in preview server — adjust port if different
})
public class ChartController {

    private static final int MAX_YEARS = 50;
    private static final int MAX_SIMULATIONS = 20_000;

    private final MonteCarloSimulationService simulationService;

    public ChartController(MonteCarloSimulationService simulationService) {
        this.simulationService = simulationService;
    }

    /**
     * Runs a Monte Carlo simulation of portfolio growth under Geometric Brownian Motion
     * and returns the 10th/50th/90th percentile trajectories, one point per year.
     *
     * DISABLED as of the client-side migration — the frontend now computes this
     * itself via js/simulation.js and never calls this endpoint. Left in place
     * (not deleted) in case a future feature needs server-side computation again
     * (e.g. a "verify this result" or seed-audit endpoint). To re-enable, just
     * uncomment the @GetMapping line below.
     *
     * @param years          projection horizon in years (default 15, 1-50)
     * @param initialCapital starting portfolio value (default 100,000, must be positive)
     * @param simulations    number of simulated paths (default 1000, 1-20,000)
     * @param mu             expected annual return, e.g. 0.07 for 7% (default 0.07)
     * @param sigma          annual volatility, e.g. 0.22 for 22% (default 0.22, must be positive)
     */
    // @GetMapping("/api/chart-data")
    public SimulationResponse getChartData(
            @RequestParam(name = "years", defaultValue = "15") int years,
            @RequestParam(name = "initialCapital", defaultValue = "100000") double initialCapital,
            @RequestParam(name = "simulations", defaultValue = "1000") int simulations,
            @RequestParam(name = "mu", defaultValue = "0.07") double mu,
            @RequestParam(name = "sigma", defaultValue = "0.22") double sigma) {

        validate(years, initialCapital, simulations, sigma);

        return simulationService.run(years, initialCapital, simulations, mu, sigma);
    }

    /**
     * Guardrails on user-supplied parameters. These are intentionally strict:
     * this endpoint runs real compute (a nested loop of years x simulations),
     * so bounds here directly protect server resources, not just data correctness.
     */
    private void validate(int years, double initialCapital, int simulations, double sigma) {
        if (years < 1 || years > MAX_YEARS) {
            throw new IllegalArgumentException("years must be between 1 and " + MAX_YEARS);
        }
        if (simulations < 1 || simulations > MAX_SIMULATIONS) {
            throw new IllegalArgumentException("simulations must be between 1 and " + MAX_SIMULATIONS);
        }
        if (initialCapital <= 0) {
            throw new IllegalArgumentException("initialCapital must be positive");
        }
        if (sigma <= 0) {
            throw new IllegalArgumentException("sigma must be positive");
        }
    }
}