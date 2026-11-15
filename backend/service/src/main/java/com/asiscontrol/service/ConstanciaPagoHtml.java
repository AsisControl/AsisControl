package com.asiscontrol.service;

import com.asiscontrol.dto.PagosFamiliaDto;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Documento autónomo, escapado e imprimible sin recursos externos ni scripts. */
final class ConstanciaPagoHtml {
    private static final Locale PERU = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("dd 'de' MMMM 'de' uuuu · HH:mm", PERU).withZone(ZoneId.of("America/Lima"));

    private ConstanciaPagoHtml() {}

    static String crear(String institucion, String referencia, String apoderado, Instant fecha,
            String medio, String moneda, List<PagosFamiliaDto.ItemResponse> items, BigDecimal total,
            List<String> comprobantes) {
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="es"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Constancia de pago</title><style>
                :root{color-scheme:light}*{box-sizing:border-box}body{margin:0;background:#eef1f7;color:#17213b;font:14px/1.6 Arial,sans-serif}
                main{max-width:900px;margin:40px auto;background:#fff;border:1px solid #dce2ee;border-radius:12px;padding:48px}
                header{display:flex;justify-content:space-between;gap:24px;border-bottom:3px solid #303a86;padding-bottom:24px}
                .brand{font-size:12px;letter-spacing:2px;text-transform:uppercase;color:#626d86}h1{margin:5px 0;font-size:26px;line-height:1.3}
                h2{font-size:20px;margin:0 0 8px}p{margin:4px 0}.documento{text-align:right}.folio{font-size:17px;font-weight:bold}
                .badge{display:inline-block;margin-top:8px;border:1px solid #bee8cc;border-radius:20px;background:#edf9f0;color:#21623b;padding:3px 12px;font-size:12px;font-weight:bold}
                dl{display:grid;grid-template-columns:1fr 1fr;gap:18px 28px;margin:28px 0}dt{font-size:11px;text-transform:uppercase;letter-spacing:1px;color:#626d86}dd{margin:2px 0;font-weight:bold;overflow-wrap:anywhere}
                table{width:100%;border-collapse:collapse;margin:24px 0}th{background:#f4f6fb;color:#57627a;font-size:11px;text-transform:uppercase;letter-spacing:.6px}
                td,th{padding:14px 12px;text-align:left;border-bottom:1px solid #e1e6ef;vertical-align:top}th:last-child,td:last-child{text-align:right;white-space:nowrap}
                small{display:block;color:#626d86;font-size:12px}.total{margin:24px 0 24px auto;max-width:330px;display:flex;justify-content:space-between;gap:24px;background:#303a86;color:white;padding:18px 20px;border-radius:8px;font-size:18px;font-weight:bold}
                .referencias{font-size:12px;color:#626d86;overflow-wrap:anywhere}footer{border-top:1px solid #e1e6ef;margin-top:32px;padding-top:18px;color:#626d86;font-size:11px}
                @page{size:A4;margin:16mm}@media print{body{background:#fff}main{margin:0;max-width:none;border:0;border-radius:0;padding:0}tr{break-inside:avoid}header,.total,footer{break-inside:avoid}}
                @media(max-width:600px){main{margin:12px;padding:22px}header{display:block}.documento{text-align:left;margin-top:20px}dl{grid-template-columns:1fr}td,th{padding:10px 6px;font-size:12px}h1{font-size:22px}}
                </style></head><body><main><header><div><p class="brand">Gestión de pagos</p><h1>
                """);
        html.append(escapar(institucion)).append("</h1></div><div class=\"documento\"><h2>Constancia de pago</h2><p class=\"folio\">")
                .append(escapar(referencia)).append("</p><span class=\"badge\">PAGADO</span></div></header><dl>")
                .append(dato("Apoderado", apoderado)).append(dato("Fecha de pago · hora de Perú", FECHA.format(fecha)))
                .append(dato("Medio de pago", medio)).append(dato("Moneda", "PEN".equals(moneda) ? "Soles (PEN)" : moneda))
                .append("</dl><table><thead><tr><th>Estudiante</th><th>Concepto y cuota</th><th>Importe pagado</th></tr></thead><tbody>");
        for (var item : items) {
            html.append("<tr><td>").append(escapar(item.alumno())).append("</td><td>")
                    .append(escapar(item.concepto()));
            if (item.numeroCuota() != null) html.append(" · Cuota ").append(item.numeroCuota());
            html.append("<small>").append(escapar(item.codigoCompromiso())).append("</small></td><td>")
                    .append(dinero(item.monto(), moneda)).append("</td></tr>");
        }
        return html.append("</tbody></table><div class=\"total\"><span>Total pagado</span><span>")
                .append(dinero(total, moneda)).append("</span></div><p class=\"referencias\">Comprobantes: ")
                .append(escapar(String.join(" · ", comprobantes)))
                .append("</p><footer>Documento de control institucional · AsisControl<br>Conserva esta constancia para consultar o identificar tu pago.</footer></main></body></html>")
                .toString();
    }

    private static String dato(String etiqueta, String valor) {
        return "<div><dt>" + escapar(etiqueta) + "</dt><dd>" + escapar(valor) + "</dd></div>";
    }

    private static String dinero(BigDecimal valor, String moneda) {
        var formato = NumberFormat.getNumberInstance(PERU);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return escapar("PEN".equals(moneda) ? "S/ " : moneda + " ") + formato.format(valor);
    }

    private static String escapar(String valor) {
        return Objects.toString(valor, "").replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
