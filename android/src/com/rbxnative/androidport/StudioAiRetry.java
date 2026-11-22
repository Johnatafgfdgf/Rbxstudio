package com.rbxnative.androidport;

/** Retry provider refusals only, never editor writes or an ambiguous transport failure. */
final class StudioAiRetry {
  static boolean provider(int status, int retries, int requests, int budget) {
    return retries == 0
        && requests < budget
        && (status == 502 || status == 503 || status == 504 || status == 529);
  }
}
