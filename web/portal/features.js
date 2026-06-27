(() => {
	const zoomSelector = [
		".features-showcase img",
		".features-detail-grid img",
		".feature-card img",
		".features-trust-band img"
	].join(", ");

	const images = Array.from(document.querySelectorAll(zoomSelector));
	if (images.length === 0) return;

	const lightbox = document.createElement("div");
	lightbox.className = "features-lightbox";
	lightbox.hidden = true;
	lightbox.setAttribute("role", "dialog");
	lightbox.setAttribute("aria-modal", "true");
	lightbox.setAttribute("aria-label", "Full-size feature image");
	lightbox.innerHTML = [
		'<button class="features-lightbox-close" type="button">Close</button>',
		'<div class="features-lightbox-frame"><img alt=""></div>',
		'<p class="features-lightbox-caption"></p>'
	].join("");
	document.body.appendChild(lightbox);

	const closeButton = lightbox.querySelector(".features-lightbox-close");
	const lightboxImage = lightbox.querySelector("img");
	const caption = lightbox.querySelector(".features-lightbox-caption");
	let previousFocus = null;

	const openLightbox = (image) => {
		previousFocus = document.activeElement;
		lightboxImage.src = image.currentSrc || image.src;
		lightboxImage.alt = image.alt || "Voidscape feature screenshot";
		caption.textContent = image.alt || "";
		lightbox.hidden = false;
		document.body.classList.add("features-lightbox-open");
		closeButton.focus({ preventScroll: true });
	};

	const closeLightbox = () => {
		if (lightbox.hidden) return;
		lightbox.hidden = true;
		lightboxImage.removeAttribute("src");
		document.body.classList.remove("features-lightbox-open");
		if (previousFocus && typeof previousFocus.focus === "function") {
			previousFocus.focus({ preventScroll: true });
		}
	};

	for (const image of images) {
		if (image.closest("button, a")) continue;

		const button = document.createElement("button");
		button.type = "button";
		button.className = "features-image-button";
		button.setAttribute("aria-label", `View full-size image: ${image.alt || "Voidscape feature screenshot"}`);

		image.parentNode.insertBefore(button, image);
		button.appendChild(image);
		button.addEventListener("click", () => openLightbox(image));
	}

	closeButton.addEventListener("click", closeLightbox);
	lightbox.addEventListener("click", (event) => {
		if (event.target === lightbox) closeLightbox();
	});
	document.addEventListener("keydown", (event) => {
		if (event.key === "Escape") closeLightbox();
	});
})();
