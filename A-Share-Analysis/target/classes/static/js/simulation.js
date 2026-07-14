/**
 * simulation.js
 *
 * Client-side port of the Monte Carlo Geometric Brownian Motion simulation
 * previously computed by MonteCarloSimulationService.java on the server.
 * Runs entirely in the browser — no network call, no backend involved.
 *
 * Load order in the HTML page:
 *   1. (optional) seedrandom CDN script — makes Math.random() seedable
 *   2. this file
 *   3. your app code, calling window.MonteCarloSimulation.runSimulation(...)
 *
 * The returned shape intentionally mirrors the old /api/chart-data response
 * (parameters / currency / series / finalValueHistogram) so nothing else in
 * the dashboard — chart rendering, CSV export — needs to change.
 */
(function (global) {
  'use strict';

  const MAX_YEARS = 50;
  const MAX_SIMULATIONS = 20000;
  const HISTOGRAM_BINS = 20;

  /**
   * Box-Muller transform: converts two independent uniform (0,1) draws into
   * one standard-normal (Gaussian) draw. This is what replaces
   * java.util.Random#nextGaussian() / ThreadLocalRandom#nextGaussian(),
   * neither of which has a direct browser equivalent.
   */
  function nextGaussian() {
    let u = 0;
    let v = 0;
    while (u === 0) u = Math.random(); // (0,1), excluding 0 to avoid log(0)
    while (v === 0) v = Math.random();
    return Math.sqrt(-2.0 * Math.log(u)) * Math.cos(2.0 * Math.PI * v);
  }

  /**
   * Computes the given percentile from a pre-sorted array using linear
   * interpolation between the two nearest ranks — same method as the
   * original Java service, so results are directly comparable.
   */
  function percentile(sortedData, pct) {
    const rank = (pct / 100) * (sortedData.length - 1);
    const lowerIndex = Math.floor(rank);
    const upperIndex = Math.ceil(rank);
    if (lowerIndex === upperIndex) return Math.round(sortedData[lowerIndex]);
    const weight = rank - lowerIndex;
    const interpolated = sortedData[lowerIndex] * (1 - weight) + sortedData[upperIndex] * weight;
    return Math.round(interpolated);
  }

  /**
   * Buckets a sorted array of final-year outcomes into a fixed number of
   * equal-width bins spanning [min, max]. Same approach as the Java version's
   * buildHistogram — counts per bin, not raw values.
   */
  function buildHistogram(sortedFinalValues, binCount) {
    const bins = [];
    const min = sortedFinalValues[0];
    const max = sortedFinalValues[sortedFinalValues.length - 1];

    if (max <= min) {
      bins.push({ rangeStart: Math.round(min), rangeEnd: Math.round(min), count: sortedFinalValues.length });
      return bins;
    }

    const binWidth = (max - min) / binCount;
    const counts = new Array(binCount).fill(0);
    for (let i = 0; i < sortedFinalValues.length; i++) {
      let binIndex = Math.floor((sortedFinalValues[i] - min) / binWidth);
      if (binIndex >= binCount) binIndex = binCount - 1; // the max value lands exactly on the boundary
      counts[binIndex]++;
    }

    for (let b = 0; b < binCount; b++) {
      bins.push({
        rangeStart: Math.round(min + b * binWidth),
        rangeEnd: Math.round(min + (b + 1) * binWidth),
        count: counts[b]
      });
    }
    return bins;
  }

  /** Same guardrails the Spring controller used to enforce server-side, plus FIRE inputs. */
  function validate({ years, initialCapital, simulations, sigma, annualSpending }) {
    if (years < 1 || years > MAX_YEARS) {
      throw new Error(`years must be between 1 and ${MAX_YEARS}`);
    }
    if (simulations < 1 || simulations > MAX_SIMULATIONS) {
      throw new Error(`simulations must be between 1 and ${MAX_SIMULATIONS}`);
    }
    if (initialCapital <= 0) {
      throw new Error('initialCapital must be positive');
    }
    if (sigma <= 0) {
      throw new Error('sigma must be positive');
    }
    if (annualSpending !== undefined && annualSpending !== null && annualSpending <= 0) {
      throw new Error('annualSpending must be positive');
    }
  }

  /**
   * Runs the Monte Carlo GBM simulation entirely client-side.
   *
   * @param {object} params
   * @param {number} params.years - projection horizon in years (the "today" -> "N years from now" window shown on the chart)
   * @param {number} params.initialCapital - starting portfolio value
   * @param {number} params.simulations - number of simulated paths
   * @param {number} params.mu - expected annual return (drift), e.g. 0.07
   * @param {number} params.sigma - annual volatility, e.g. 0.22
   * @param {number} [params.annualSpending] - planned annual spending in retirement.
   *   If provided, the FIRE line is computed via the 4% rule (threshold = annualSpending / 0.04)
   *   and a success-rate-by-year curve is returned in the `fire` field. Omit to skip FIRE analysis entirely.
   * @param {number} [params.headStartYears] - simulates this many EXTRA years of compounding
   *   before "today" (year 0), then reports everything (chart, histogram, FIRE curve) on the
   *   normal 0..years axis as if that head start had already happened. This is what powers the
   *   "what if I'd started investing N years earlier" comparison — it is a real additional
   *   Monte Carlo stretch of compounding, not an analytical shortcut.
   * @param {string|number} [params.seed] - optional seed for reproducibility. If omitted, a
   *   random seed is generated and returned so the caller can build a shareable link, or reuse
   *   the same seed for an apples-to-apples "what if" comparison against this exact run.
   */
  function runSimulation(params) {
    const { years, initialCapital, simulations, mu, sigma, annualSpending } = params;
    const headStartYears = Number(params.headStartYears) || 0;
    validate({ years, initialCapital, simulations, sigma, annualSpending });

    const totalYears = years + headStartYears;
    if (totalYears > MAX_YEARS) {
      throw new Error(`years + headStartYears cannot exceed ${MAX_YEARS} (currently ${totalYears})`);
    }

    // If the seedrandom CDN script is loaded, reseed Math.random() so this
    // run's random draws are fully determined by `resolvedSeed`. Without it,
    // this still works — it just isn't reproducible across page loads.
    const resolvedSeed = (params.seed !== undefined && params.seed !== null && params.seed !== '')
      ? String(params.seed)
      : String(Math.floor(Math.random() * 1e9));
    if (typeof Math.seedrandom === 'function') {
      Math.seedrandom(resolvedSeed);
    }

    const fireThreshold = (annualSpending !== undefined && annualSpending !== null && annualSpending > 0)
      ? annualSpending / 0.04
      : null;

    // yearlyResults[t][i] = simulated portfolio value at simulated-year t (0..totalYears) for path i
    const yearlyResults = [];
    for (let t = 0; t <= totalYears; t++) yearlyResults.push(new Array(simulations));
    for (let i = 0; i < simulations; i++) yearlyResults[0][i] = initialCapital;

    // rawFireYear[i] = the first simulated-year index (0..totalYears) at which path i's value
    // reached the FIRE threshold, or null if it never did within the simulated horizon. Tracked
    // inline during the main loop — before any sorting happens — since sorting for percentiles
    // later would otherwise destroy each path's identity across years.
    const rawFireYear = fireThreshold !== null ? new Array(simulations).fill(null) : null;

    for (let i = 0; i < simulations; i++) {
      let current = initialCapital;
      if (rawFireYear && current >= fireThreshold) rawFireYear[i] = 0;
      for (let t = 1; t <= totalYears; t++) {
        const z = nextGaussian();
        current = current * Math.exp((mu - 0.5 * sigma * sigma) + sigma * z);
        yearlyResults[t][i] = current;
        if (rawFireYear && rawFireYear[i] === null && current >= fireThreshold) {
          rawFireYear[i] = t;
        }
      }
    }

    // Everything reported below is windowed by headStartYears: simulated-year
    // (headStartYears + t) becomes displayed "year t", so a head start is
    // reflected in the chart, histogram, and FIRE curve consistently.
    const series = [];
    for (let t = 0; t <= years; t++) {
      const row = yearlyResults[t + headStartYears].slice().sort((a, b) => a - b);
      series.push({
        year: t,
        p10: percentile(row, 10),
        p50: percentile(row, 50),
        p90: percentile(row, 90)
      });
    }

    const finalRow = yearlyResults[years + headStartYears].slice().sort((a, b) => a - b);
    const finalValueHistogram = buildHistogram(finalRow, HISTOGRAM_BINS);

    let fire = null;
    if (fireThreshold !== null) {
      // Bucket each path's (head-start-adjusted) crossing year, then take a
      // running cumulative count — O(simulations + years) rather than
      // re-scanning every path for every year.
      const bucketCounts = new Array(years + 1).fill(0);
      for (let i = 0; i < simulations; i++) {
        if (rawFireYear[i] === null) continue;
        const effectiveYear = Math.max(0, rawFireYear[i] - headStartYears);
        if (effectiveYear <= years) bucketCounts[effectiveYear]++;
      }

      const successRateByYear = [];
      let cumulative = 0;
      let medianYearToFire = null;
      for (let t = 0; t <= years; t++) {
        cumulative += bucketCounts[t];
        const successRate = cumulative / simulations;
        successRateByYear.push({ year: t, successRate });
        if (medianYearToFire === null && successRate >= 0.5) medianYearToFire = t;
      }

      fire = {
        threshold: fireThreshold,
        annualSpending,
        headStartYears,
        successRateByYear,
        overallSuccessRate: successRateByYear[years].successRate,
        medianYearToFire // null means fewer than half of paths reached FIRE within the horizon
      };
    }

    return {
      parameters: { years, initialCapital, simulations, mu, sigma },
      currency: 'USD',
      series,
      finalValueHistogram,
      fire,
      seed: resolvedSeed
    };
  }

  global.MonteCarloSimulation = { runSimulation };
})(window);