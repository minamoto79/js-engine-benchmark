function sieve(n) {
    const isPrime = new Uint8Array(n);
    for (let i = 2; i < n; i++) isPrime[i] = 1;
    for (let i = 2; i * i < n; i++) {
        if (isPrime[i]) {
            for (let j = i * i; j < n; j += i) isPrime[j] = 0;
        }
    }
    let count = 0;
    for (let i = 2; i < n; i++) if (isPrime[i]) count++;
    return count;
}
