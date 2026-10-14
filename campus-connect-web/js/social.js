// ============================================================================
// MÓDULO DE DIFUSIÓN EN REDES SOCIALES (FACEBOOK E INSTAGRAM)
// Soporta:
// 1. Publicación Directa Automática (Foto + Texto exacto) vía Meta Graph API
//    o Backend FastAPI, funcionando incluso desde 127.0.0.1 (localhost)
// 2. Generador de Postal Visual Unificada (Flyer + Texto en una sola imagen)
// 3. Copiado inteligente al portapapeles sin el cuadro roto "127.0.0.1"
// ============================================================================

const STORAGE_META_CONFIG = "cc_unach_meta_api_config_v1";
let socialActual = null;

function obtenerConfigMetaLocal() {
    try {
        return JSON.parse(localStorage.getItem(STORAGE_META_CONFIG) || "{}") || {};
    } catch (_) {
        return {};
    }
}

function guardarConfigMetaLocal(e) {
    if (e) e.preventDefault();
    const cfg = {
        fbPageId: (document.getElementById("meta-fb-page-id")?.value || "").trim(),
        igUserId: (document.getElementById("meta-ig-user-id")?.value || "").trim(),
        accessToken: (document.getElementById("meta-access-token")?.value || "").trim()
    };
    localStorage.setItem(STORAGE_META_CONFIG, JSON.stringify(cfg));
    document.getElementById("panel-config-meta")?.classList.add("hidden");
    actualizarEstadoBotonesSocial();
    toast("Configuración de Facebook e Instagram guardada correctamente.", "exito");
}

function togglePanelConfigMeta() {
    const panel = document.getElementById("panel-config-meta");
    if (!panel) return;
    const cfg = obtenerConfigMetaLocal();
    if (document.getElementById("meta-fb-page-id")) document.getElementById("meta-fb-page-id").value = cfg.fbPageId || "";
    if (document.getElementById("meta-ig-user-id")) document.getElementById("meta-ig-user-id").value = cfg.igUserId || "";
    if (document.getElementById("meta-access-token")) document.getElementById("meta-access-token").value = cfg.accessToken || "";
    panel.classList.toggle("hidden");
}

function actualizarVistaPreviaSocialEnVivo() {
    const texto = document.getElementById("social-texto")?.value || "";
    const previewTexto = document.getElementById("social-preview-caption");
    if (previewTexto) {
        previewTexto.textContent = texto;
    }
}

function esUrlLocal(url) {
    if (!url) return true;
    return url.includes("127.0.0.1") || url.includes("localhost") || url.includes("0.0.0.0");
}

async function abrirSocial(eventoId) {
    if (!tienePermiso("redes.compartir")) {
        toast("No tienes permiso para compartir eventos en redes sociales.", "alerta");
        return;
    }
    try {
        const [post, estado] = await Promise.all([
            api(`/social/eventos/${eventoId}/post`),
            api("/social/estado").catch(() => ({ facebookApi: false, instagramApi: false }))
        ]);
        socialActual = { id: eventoId, estadoBackend: estado, ...post };

        const txtEl = document.getElementById("social-texto");
        txtEl.value = post.texto || "";
        actualizarVistaPreviaSocialEnVivo();

        const img = document.getElementById("social-imagen");
        const sinImg = document.getElementById("social-sin-imagen");
        if (post.imagenUrl) {
            img.src = post.imagenUrl;
            img.classList.remove("hidden");
            sinImg?.classList.add("hidden");
        } else {
            img.classList.add("hidden");
            sinImg?.classList.remove("hidden");
        }

        actualizarEstadoBotonesSocial();
        document.getElementById("modal-social").classList.remove("hidden");
    } catch (err) {
        toast(err.message, "error");
    }
}

function actualizarEstadoBotonesSocial() {
    if (!socialActual) return;
    const cfg = obtenerConfigMetaLocal();
    const backendFB = Boolean(socialActual.estadoBackend?.facebookApi);
    const backendIG = Boolean(socialActual.estadoBackend?.instagramApi);
    const localFB = Boolean(cfg.fbPageId && cfg.accessToken);
    const localIG = Boolean(cfg.igUserId && cfg.accessToken && cfg.fbPageId);

    const fbListo = backendFB || localFB;
    const igListo = backendIG || localIG;

    const badgeEstado = document.getElementById("social-api-status-badge");
    if (badgeEstado) {
        if (fbListo || igListo) {
            badgeEstado.className = "text-[11px] font-extrabold px-2.5 py-1 rounded-full bg-emerald-100 text-emerald-800 border border-emerald-200";
            badgeEstado.innerHTML = `<i class="fa-solid fa-circle-check mr-1"></i>API Conectada (${[fbListo ? "Facebook" : null, igListo ? "Instagram" : null].filter(Boolean).join(" + ")})`;
        } else {
            badgeEstado.className = "text-[11px] font-extrabold px-2.5 py-1 rounded-full bg-amber-100 text-amber-800 border border-amber-200";
            badgeEstado.innerHTML = `<i class="fa-solid fa-plug-circle-exclamation mr-1"></i>Configura tu Token para publicar en 1 clic`;
        }
    }
}

function cerrarModalSocial() {
    document.getElementById("modal-social").classList.add("hidden");
}

async function copiarTexto(texto) {
    try {
        await navigator.clipboard.writeText(texto);
        return true;
    } catch (_) {
        return false;
    }
}

// ============================================================================
// PUBLICACIÓN AUTOMÁTICA REAL (IMAGEN + TEXTO EXACTO) EN FACEBOOK E INSTAGRAM
// Funciona incluso cuando el servidor corre en 127.0.0.1 (localhost)
// ============================================================================
async function obtenerBlobImagenEvento() {
    if (!socialActual?.imagenUrl) return null;
    const res = await fetch(socialActual.imagenUrl);
    if (!res.ok) throw new Error("No se pudo leer el archivo del flyer del servidor.");
    return await res.blob();
}

// Sube el archivo binario desde el navegador a Facebook Graph API y devuelve { postId, photoId, publicCdnUrl }
async function subirFotoAFacebookGraph(cfg, texto, publicarEnFeed = true) {
    const blob = await obtenerBlobImagenEvento();
    if (!blob) {
        // Si el evento no tiene imagen, publicar solo texto en el feed de la página
        if (!publicarEnFeed) throw new Error("Instagram requiere que el evento tenga una imagen o flyer.");
        const fdText = new FormData();
        fdText.append("message", texto);
        fdText.append("access_token", cfg.accessToken);
        const r = await fetch(`https://graph.facebook.com/v19.0/${encodeURIComponent(cfg.fbPageId)}/feed`, {
            method: "POST",
            body: fdText
        });
        const d = await r.json();
        if (!r.ok || d.error) throw new Error(d.error?.message || "Error al publicar en Facebook.");
        return { postId: d.id, photoId: null, publicCdnUrl: null };
    }

    const fd = new FormData();
    fd.append("source", blob, "flyer_evento_unach.jpg");
    if (publicarEnFeed) {
        fd.append("message", texto);
        fd.append("published", "true");
    } else {
        // Si solo necesitamos alojar temporalmente la imagen en el CDN de Meta para que Instagram la lea desde localhost
        fd.append("published", "false");
    }
    fd.append("access_token", cfg.accessToken);

    const res = await fetch(`https://graph.facebook.com/v19.0/${encodeURIComponent(cfg.fbPageId)}/photos`, {
        method: "POST",
        body: fd
    });
    const data = await res.json();
    if (!res.ok || data.error) {
        throw new Error(data.error?.message || "Error al subir el flyer a Facebook Graph API.");
    }

    // Obtener la URL pública HTTPS en el CDN de Meta (fbcdn.net) para que Instagram pueda descargarla aunque estemos en 127.0.0.1
    let publicCdnUrl = null;
    try {
        const resInfo = await fetch(`https://graph.facebook.com/v19.0/${data.id}?fields=images&access_token=${encodeURIComponent(cfg.accessToken)}`);
        const infoData = await resInfo.json();
        if (infoData.images && infoData.images.length > 0) {
            publicCdnUrl = infoData.images[0].source;
        }
    } catch (_) {
        /* opcional */
    }

    return {
        postId: data.post_id || data.id,
        photoId: data.id,
        publicCdnUrl
    };
}

async function publicarEnInstagramGraph(cfg, texto, urlImagenPublica) {
    if (!urlImagenPublica) {
        throw new Error("Instagram requiere una imagen (flyer) en el evento para poder publicar.");
    }

    // Paso 1: Crear contenedor multimedia en Instagram Graph API
    const paramsContainer = new URLSearchParams({
        image_url: urlImagenPublica,
        caption: texto,
        access_token: cfg.accessToken
    });
    const resCont = await fetch(`https://graph.facebook.com/v19.0/${encodeURIComponent(cfg.igUserId)}/media`, {
        method: "POST",
        body: paramsContainer
    });
    const dataCont = await resCont.json();
    if (!resCont.ok || dataCont.error) {
        throw new Error(dataCont.error?.message || "No se pudo crear la publicación en Instagram.");
    }

    // Paso 2: Publicar el contenedor en el Feed de Instagram
    const paramsPub = new URLSearchParams({
        creation_id: dataCont.id,
        access_token: cfg.accessToken
    });
    const resPub = await fetch(`https://graph.facebook.com/v19.0/${encodeURIComponent(cfg.igUserId)}/media_publish`, {
        method: "POST",
        body: paramsPub
    });
    const dataPub = await resPub.json();
    if (!resPub.ok || dataPub.error) {
        throw new Error(dataPub.error?.message || "No se pudo finalizar la publicación en Instagram.");
    }
    return dataPub.id;
}

// Función principal para publicar exactamente igual a la vista previa (Flyer + Texto)
async function publicarDirectoEnRed(plataforma) {
    const texto = document.getElementById("social-texto").value.trim();
    const cfg = obtenerConfigMetaLocal();
    const tieneConfigLocal = (plataforma === "facebook" && cfg.fbPageId && cfg.accessToken) ||
                             (plataforma === "instagram" && cfg.igUserId && cfg.fbPageId && cfg.accessToken) ||
                             (plataforma === "ambas" && cfg.fbPageId && cfg.igUserId && cfg.accessToken);

    const tieneBackend = (plataforma === "facebook" && socialActual?.estadoBackend?.facebookApi) ||
                         (plataforma === "instagram" && socialActual?.estadoBackend?.instagramApi) ||
                         (plataforma === "ambas" && socialActual?.estadoBackend?.facebookApi && socialActual?.estadoBackend?.instagramApi);

    // Si aún no ha configurado el Token ni en el navegador ni en el backend, abrir el asistente de configuración
    if (!tieneConfigLocal && !tieneBackend) {
        document.getElementById("panel-config-meta")?.classList.remove("hidden");
        toast("Configura tu Page ID y Access Token de Meta aquí arriba para publicar con 1 clic, o usa los botones de abajo para copiar la postal.", "alerta");
        return;
    }

    const btnId = plataforma === "facebook" ? "btn-pub-directo-fb" : (plataforma === "instagram" ? "btn-pub-directo-ig" : "btn-pub-directo-ambas");
    const btn = document.getElementById(btnId);
    if (btn) {
        btn.disabled = true;
        btn.classList.add("opacity-60");
    }

    try {
        toast(`Publicando flyer y texto en ${plataforma === "ambas" ? "Facebook e Instagram" : plataforma}...`, "info");

        if (tieneConfigLocal) {
            if (plataforma === "facebook") {
                await subirFotoAFacebookGraph(cfg, texto, true);
                toast("¡Publicado en Facebook con el flyer y texto exactos!", "exito");
            } else if (plataforma === "instagram") {
                // Si la URL es 127.0.0.1, usamos el puente del CDN de Meta (published=false) para que Instagram tenga URL HTTPS pública
                let urlPublica = (!esUrlLocal(socialActual.imagenUrl)) ? socialActual.imagenUrl : null;
                if (!urlPublica) {
                    const puente = await subirFotoAFacebookGraph(cfg, texto, false);
                    urlPublica = puente.publicCdnUrl;
                }
                await publicarEnInstagramGraph(cfg, texto, urlPublica);
                toast("¡Publicado en Instagram con el flyer y texto exactos!", "exito");
            } else if (plataforma === "ambas") {
                const fbRes = await subirFotoAFacebookGraph(cfg, texto, true);
                const urlParaIg = fbRes.publicCdnUrl || (!esUrlLocal(socialActual.imagenUrl) ? socialActual.imagenUrl : null);
                await publicarEnInstagramGraph(cfg, texto, urlParaIg);
                toast("¡Publicado simultáneamente en Facebook e Instagram!", "exito");
            }
        } else {
            // Usar el endpoint del backend FastAPI
            if (plataforma === "ambas") {
                await api(`/social/eventos/${socialActual.id}/publicar`, { method: "POST", json: { plataforma: "facebook", texto } });
                await api(`/social/eventos/${socialActual.id}/publicar`, { method: "POST", json: { plataforma: "instagram", texto } });
                toast("¡Publicado en Facebook e Instagram desde el servidor!", "exito");
            } else {
                const r = await api(`/social/eventos/${socialActual.id}/publicar`, {
                    method: "POST",
                    json: { plataforma, texto }
                });
                toast(r.mensaje || "Publicado exitosamente.", "exito");
            }
        }
    } catch (err) {
        toast(err.message, "error");
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.classList.remove("opacity-60");
        }
    }
}

// ============================================================================
// GENERADOR DE POSTAL COMPLETA (FLYER + TEXTO EN UNA SOLA IMAGEN HD)
// Y COPIADO AL PORTAPAPELES SIN EL CUADRO ROTO "127.0.0.1"
// ============================================================================
async function generarCanvasPostalCompleta() {
    const texto = document.getElementById("social-texto").value.trim();
    const lineasRaw = texto.split("\n");

    const canvas = document.createElement("canvas");
    const W = 1080;
    const pad = 56;
    const ctx = canvas.getContext("2d");

    // Cargar imagen del flyer si existe
    let imgObj = null;
    let imgH = 0;
    if (socialActual?.imagenUrl) {
        try {
            imgObj = await new Promise((resolve, reject) => {
                const im = new Image();
                im.crossOrigin = "anonymous";
                im.onload = () => resolve(im);
                im.onerror = reject;
                im.src = socialActual.imagenUrl;
            });
            const ratio = imgObj.height / imgObj.width;
            imgH = Math.min(Math.round(W * ratio), 760);
        } catch (_) {
            imgObj = null;
        }
    }

    // Envolver líneas de texto para calcular alto dinámico
    ctx.font = "500 28px Inter, Segoe UI, Arial, sans-serif";
    const maxTextW = W - pad * 2;
    const lineasEnvueltas = [];
    lineasRaw.forEach((linea, idx) => {
        if (!linea.trim()) {
            lineasEnvueltas.push({ texto: "", esTitulo: false });
            return;
        }
        const palabras = linea.split(" ");
        let actual = "";
        palabras.forEach(p => {
            const prueba = actual ? `${actual} ${p}` : p;
            if (ctx.measureText(prueba).width > maxTextW) {
                lineasEnvueltas.push({ texto: actual, esTitulo: idx === 0 });
                actual = p;
            } else {
                actual = prueba;
            }
        });
        if (actual) lineasEnvueltas.push({ texto: actual, esTitulo: idx === 0 });
    });

    const headerH = 110;
    const textBlockH = Math.max(lineasEnvueltas.length * 42 + 70, 220);
    const footerH = 68;
    canvas.width = W;
    canvas.height = headerH + imgH + textBlockH + footerH;

    // 1. Fondo blanco general
    ctx.fillStyle = "#FFFFFF";
    ctx.fillRect(0, 0, W, canvas.height);

    // 2. Cabecera Institucional UNACH • ETDA
    ctx.fillStyle = "#122652";
    ctx.fillRect(0, 0, W, headerH);
    ctx.fillStyle = "#F57C00";
    ctx.fillRect(0, headerH - 6, W, 6);

    ctx.fillStyle = "#F57C00";
    ctx.beginPath();
    ctx.arc(pad + 26, headerH / 2 - 2, 26, 0, Math.PI * 2);
    ctx.fill();

    ctx.fillStyle = "#FFFFFF";
    ctx.font = "bold 26px Inter, Segoe UI, Arial, sans-serif";
    ctx.fillText("Campus Connect UNACH", pad + 68, headerH / 2 - 6);
    ctx.fillStyle = "#FFB74D";
    ctx.font = "bold 17px Inter, Segoe UI, Arial, sans-serif";
    ctx.fillText("Escuela de Tecnologías Digitales Aplicadas (ETDA) • Cartelera Oficial", pad + 68, headerH / 2 + 22);

    // 3. Dibujar el Flyer del evento (si existe)
    let cursorY = headerH;
    if (imgObj && imgH > 0) {
        ctx.fillStyle = "#0F172A";
        ctx.fillRect(0, cursorY, W, imgH);
        ctx.drawImage(imgObj, 0, cursorY, W, imgH);
        cursorY += imgH;
    }

    // 4. Dibujar el bloque de texto tal como se ve en la vista previa
    cursorY += 48;
    lineasEnvueltas.forEach(item => {
        if (item.esTitulo) {
            ctx.fillStyle = "#122652";
            ctx.font = "bold 34px Inter, Segoe UI, Arial, sans-serif";
        } else {
            ctx.fillStyle = "#1E293B";
            ctx.font = "500 26px Inter, Segoe UI, Arial, sans-serif";
        }
        ctx.fillText(item.texto, pad, cursorY);
        cursorY += item.esTitulo ? 48 : 40;
    });

    // 5. Pie institucional
    const yFooter = canvas.height - footerH;
    ctx.fillStyle = "#F4F7FB";
    ctx.fillRect(0, yFooter, W, footerH);
    ctx.fillStyle = "#122652";
    ctx.font = "bold 18px Inter, Segoe UI, Arial, sans-serif";
    ctx.fillText("“Por la conciencia de la necesidad de servir” — UNACH Campus I", pad, yFooter + 42);

    return canvas;
}

// Copia la IMAGEN al portapapeles de Windows/Mac (para pegar con Ctrl+V en Facebook) y descarga o copia el texto
async function copiarFlyerYTextoPortapapeles() {
    const texto = document.getElementById("social-texto").value;
    await copiarTexto(texto);

    try {
        const canvas = await generarCanvasPostalCompleta();
        const blob = await new Promise(res => canvas.toBlob(res, "image/png"));
        if (navigator.clipboard && window.ClipboardItem) {
            await navigator.clipboard.write([
                new ClipboardItem({ "image/png": blob })
            ]);
            toast("¡Imagen de la publicación copiada al portapapeles! En Facebook solo presiona Ctrl + V.", "exito");
            return true;
        }
    } catch (_) {
        /* Si el navegador restringe ClipboardItem de imagen, dejamos el texto copiado */
    }
    toast("Texto copiado al portapapeles.", "exito");
    return false;
}

async function descargarPostalCompletaPNG() {
    try {
        toast("Generando imagen completa del post (Flyer + Información)...", "info");
        const canvas = await generarCanvasPostalCompleta();
        const url = canvas.toDataURL("image/png");
        const a = Object.assign(document.createElement("a"), {
            href: url,
            download: `post_evento_unach_${socialActual?.id || "etda"}.png`
        });
        document.body.appendChild(a);
        a.click();
        a.remove();
        await copiarTexto(document.getElementById("social-texto").value);
        toast("¡Postal descargada en PNG y texto copiado al portapapeles!", "exito");
    } catch (err) {
        toast("No se pudo generar la postal: " + err.message, "error");
    }
}

// Abrir Facebook de forma limpia (NUNCA envía 127.0.0.1 a sharer.php)
async function compartirFacebook() {
    const texto = document.getElementById("social-texto").value;
    await copiarFlyerYTextoPortapapeles();

    // Si el servidor tiene un dominio público real (HTTPS), usamos sharer.php;
    // si está en 127.0.0.1 / localhost, NO enviamos 127.0.0.1 para evitar el recuadro gris roto
    if (socialActual?.enlacePublico && !esUrlLocal(socialActual.enlacePublico)) {
        const url = "https://www.facebook.com/sharer/sharer.php?" + new URLSearchParams({
            u: socialActual.enlacePublico
        });
        window.open(url, "_blank", "noopener,width=680,height=640");
    } else {
        await descargarPostalCompletaPNG();
        window.open("https://www.facebook.com/", "_blank", "noopener,width=820,height=720");
        toast("Se copió la publicación y se descargó la imagen: haz clic en '¿Qué estás pensando?' y presiona Ctrl + V.", "info");
    }
}

// Preparar o compartir en Instagram con la postal completa (Flyer + Texto)
async function compartirInstagram() {
    await descargarPostalCompletaPNG();
    window.open("https://www.instagram.com/", "_blank", "noopener,width=820,height=720");
    toast("Se descargó la Postal Completa (Flyer + Información) y se copió el texto: haz clic en '+' (Crear) en Instagram y súbela.", "exito");
}
