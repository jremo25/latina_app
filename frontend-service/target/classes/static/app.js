/**
 * app.js — Inspire front-end logic
 *
 * Flow:
 *  1. On page load, call /api/config to get the URLs of the two backend services.
 *     This way the URLs are never hardcoded here — they come from the server,
 *     which reads them from environment variables at runtime.
 *  2. On button click, call both services simultaneously with Promise.all().
 *  3. Swap in the new image and phrase, animating the transition.
 */

(function () {
    'use strict';

    // ── DOM references ───────────────────────────────────────────────────────
    const btn         = document.getElementById('inspireBtn');
    const emptyState  = document.getElementById('emptyState');
    const card        = document.getElementById('card');
    const cardImage   = document.getElementById('cardImage');
    const cardPhrase  = document.getElementById('cardPhrase');
    const skeleton    = document.getElementById('skeleton');
    const errorBox    = document.getElementById('errorBox');
    const errorMsg    = document.getElementById('errorMessage');
    const triggerHint = document.getElementById('triggerHint');

    // ── State ────────────────────────────────────────────────────────────────
    let imageServiceUrl  = null;
    let phraseServiceUrl = null;
    let firstLoad        = true;

    // ── Bootstrap — load config from Spring Boot /api/config ────────────────
    async function loadConfig() {
        try {
            const res = await fetch('/api/config');
            if (!res.ok) throw new Error('Config endpoint returned ' + res.status);
            const cfg = await res.json();
            imageServiceUrl  = cfg.imageServiceUrl;
            phraseServiceUrl = cfg.phraseServiceUrl;
            btn.disabled = false;
        } catch (err) {
            showError('Could not load service configuration: ' + err.message);
            btn.disabled = true;
        }
    }

    // ── Fetch both services in parallel ─────────────────────────────────────
    async function fetchInspiration() {
        setLoading(true);
        try {
            const [imageRes, phraseRes] = await Promise.all([
                fetch(imageServiceUrl  + '/api/image'),
                fetch(phraseServiceUrl + '/api/phrase')
            ]);

            if (!imageRes.ok)  throw new Error('Image service returned ' + imageRes.status);
            if (!phraseRes.ok) throw new Error('Phrase service returned ' + phraseRes.status);

            const [imageData, phraseData] = await Promise.all([
                imageRes.json(),
                phraseRes.json()
            ]);

            displayResult(imageData.url, phraseData.phrase);
        } catch (err) {
            showError(err.message || 'One or more services are unreachable.');
        } finally {
            setLoading(false);
        }
    }

    // ── Display the result ───────────────────────────────────────────────────
    function displayResult(imageUrl, phrase) {
        hideError();

        // Build full image URL: if the returned URL is relative (starts with /),
        // it points to image-service itself, so prepend the image service base URL.
        const fullImageUrl = imageUrl.startsWith('http')
            ? imageUrl
            : imageServiceUrl + imageUrl;

        // Fade out the card if it is already visible, then swap content
        if (!firstLoad) {
            card.style.opacity = '0';
            card.style.transform = 'translateY(8px)';
        }

        const img = new Image();
        img.onload = () => {
            cardImage.src = fullImageUrl;
            cardPhrase.textContent = phrase;

            if (firstLoad) {
                emptyState.style.display = 'none';
                card.classList.add('visible');
                triggerHint.textContent = 'click again for a new vision';
                firstLoad = false;
            } else {
                // Re-trigger animation by removing and re-adding the class
                card.classList.remove('visible');
                void card.offsetWidth; // force reflow
                card.style.opacity = '';
                card.style.transform = '';
                card.classList.add('visible');
            }
        };
        img.onerror = () => {
            // Image URL valid JSON but file not found — still show the phrase
            cardImage.src = '';
            cardImage.alt = 'Image unavailable';
            cardPhrase.textContent = phrase;
            card.classList.add('visible');
            emptyState.style.display = 'none';
            firstLoad = false;
        };
        img.src = fullImageUrl;
    }

    // ── UI state helpers ─────────────────────────────────────────────────────
    function setLoading(on) {
        btn.disabled = on;
        skeleton.hidden = !on;
        if (on) {
            card.classList.remove('visible');
            hideError();
        } else {
            skeleton.hidden = true;
        }
    }

    function showError(message) {
        errorMsg.textContent = message;
        errorBox.hidden = false;
        card.classList.remove('visible');
        if (firstLoad) emptyState.style.display = '';
    }

    function hideError() {
        errorBox.hidden = true;
    }

    // ── Wire up events ───────────────────────────────────────────────────────
    btn.addEventListener('click', fetchInspiration);

    // Disable button until config is loaded
    btn.disabled = true;

    // Start
    loadConfig();

})();
