package com.asiscontrol.service;

import com.asiscontrol.dto.academico.PlanEstudiosDtos.CursoDelPlan;
import com.asiscontrol.dto.academico.PlanEstudiosDtos.PlanAnual;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.base.JRBasePrintPage;
import net.sf.jasperreports.engine.base.JRBasePrintRectangle;
import net.sf.jasperreports.engine.base.JRBasePrintText;
import net.sf.jasperreports.engine.type.HorizontalTextAlignEnum;
import net.sf.jasperreports.engine.type.HyperlinkTypeEnum;
import net.sf.jasperreports.engine.type.ModeEnum;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.text.AttributedString;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

@Component
public class GeneradorPlanEstudios {
    private static final int ANCHO = 842;
    private static final int ALTO = 595;
    private static final int MARGEN = 29;
    private static final int CONTENIDO = ANCHO - 2 * MARGEN;
    private static final int COLUMNA = 149;
    private static final int PASO = 158;
    private static final int ALTO_FILAS = 250;
    private static final int TAMANO_CURSO = 10;
    private static final int INTERLINEADO = 11;
    private static final Color AZUL = Color.decode("#1E3A8A");
    private static final Color PANEL_AZUL = Color.decode("#244D9A");
    private static final Color TINTA = Color.decode("#121E37");
    private static final Color SECUNDARIO = Color.decode("#4B5B74");
    private static final Color BORDE = Color.decode("#D8E2F2");
    private static final Color CLARO = Color.decode("#F2F6FC");
    private static final Color FONDO = Color.decode("#F7FAFF");
    private static final Color BLANCO_AZULADO = Color.decode("#DBE8FC");
    private static final Color[] ACENTOS = {
            Color.decode("#1E49A0"), Color.decode("#6D3398"), Color.decode("#007168"),
            Color.decode("#A44918"), Color.decode("#126290"), Color.decode("#427125"),
            Color.decode("#964369")
    };
    private static final String FUENTE = "DejaVu Sans";
    private static final String NORMA =
            "https://www.gob.pe/institucion/minedu/normas-legales/5518274-048-2-24-minedu";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final FontRenderContext MEDICION = new FontRenderContext(null, true, true);
    private static final Font REGULAR = fuente("DejaVuSans.ttf");
    private static final Font NEGRITA = fuente("DejaVuSans-Bold.ttf");

    public byte[] generar(PlanAnual plan) {
        var documento = new JasperPrint();
        documento.setName(plan.nombre());
        documento.setPageWidth(ANCHO);
        documento.setPageHeight(ALTO);
        var paginas = paginar(filas(plan));
        int total = paginas.size() + 1;
        for (int i = 0; i < paginas.size(); i++) {
            documento.addPage(mapa(documento, plan, paginas.get(i), i + 1, total));
        }
        documento.addPage(condiciones(documento, total));
        try {
            return JasperExportManager.exportReportToPdf(documento);
        } catch (JRException e) {
            throw new IllegalStateException("No se pudo generar el plan de estudios", e);
        }
    }

    private List<Fila> filas(PlanAnual plan) {
        var grupos = new TreeMap<String, List<Tarjeta[]>>(String.CASE_INSENSITIVE_ORDER);
        for (var grado : plan.grados()) {
            for (var curso : grado.cursos()) {
                var iguales = grupos.computeIfAbsent(curso.nombre().strip(), clave -> new ArrayList<>());
                int columna = grado.numero() - 1;
                var fila = iguales.stream().filter(celdas -> celdas[columna] == null)
                        .findFirst().orElseGet(() -> {
                            var nueva = new Tarjeta[5];
                            iguales.add(nueva);
                            return nueva;
                        });
                fila[columna] = new Tarjeta(curso, envolver(curso.nombre(), COLUMNA - 28, NEGRITA, TAMANO_CURSO));
            }
        }
        var resultado = new ArrayList<Fila>();
        int indiceColor = 0;
        for (var iguales : grupos.values()) {
            Color color = ACENTOS[indiceColor++ % ACENTOS.length];
            for (var celdas : iguales) {
                int alto = 34;
                for (var tarjeta : celdas) {
                    if (tarjeta != null) alto = Math.max(alto, 22 + INTERLINEADO * tarjeta.lineas().size());
                }
                resultado.add(new Fila(celdas, color, alto));
            }
        }
        return resultado;
    }

    private List<List<Fila>> paginar(List<Fila> filas) {
        var paginas = new ArrayList<List<Fila>>();
        var actual = new ArrayList<Fila>();
        int ocupado = 0;
        for (var fila : filas) {
            if (!actual.isEmpty() && ocupado + fila.alto() > ALTO_FILAS) {
                paginas.add(actual);
                actual = new ArrayList<>();
                ocupado = 0;
            }
            actual.add(fila);
            ocupado += fila.alto();
        }
        paginas.add(actual);
        return paginas;
    }

    private JRBasePrintPage mapa(JasperPrint documento, PlanAnual plan, List<Fila> filas, int numero, int total) {
        var p = new Pagina(documento);
        String estado = switch (plan.estado()) {
            case ACTIVO -> "AÑO ACADÉMICO ACTIVO";
            case BORRADOR -> "AÑO ACADÉMICO EN BORRADOR";
            case CERRADO -> "AÑO ACADÉMICO CERRADO";
        };
        cabecera(p, "Plan de estudios", plan.nombre(), estado, String.valueOf(plan.anio()),
                FECHA.format(plan.inicio()) + " a " + FECHA.format(plan.fin()));
        p.texto(MARGEN, 119, 410, "Cursos del plan por grado", 12, TINTA, true);
        if (plan.borrador()) {
            p.rectangulo(479, 118, 150, 17, 4, Color.decode("#FFF1D6"), null);
            p.texto(486, 120, 138, "BORRADOR DEL PLAN", 9, Color.decode("#854B12"), true);
        }
        p.derecha(642, 120, 171, "h/sem = horas semanales", 10, SECUNDARIO, false);
        for (var grado : plan.grados()) {
            int x = columna(grado.numero() - 1);
            p.rectangulo(x, 142, COLUMNA, 303, 8, FONDO, BORDE);
            p.rectangulo(x + 6, 148, COLUMNA - 12, 36, 5, CLARO, null);
            p.texto(x + 12, 151, COLUMNA - 24, grado.numero() + ".º de secundaria", 12, AZUL, true);
            String resumen = !grado.definido() ? "Grado sin plan definido"
                    : grado.cursos().size() + " cursos · " + grado.horasSemanales() + " h/sem";
            p.texto(x + 12, 167, COLUMNA - 24, resumen, 9, SECUNDARIO, false);
        }
        int y = 190;
        for (var fila : filas) {
            for (int i = 0; i < fila.celdas().length; i++) {
                var tarjeta = fila.celdas()[i];
                if (tarjeta != null) dibujarCurso(p, columna(i), y, fila, tarjeta);
            }
            y += fila.alto();
        }
        p.texto(MARGEN, 451, CONTENIDO, "La misma fila y el mismo color indican continuidad curricular entre grados.", 10, SECUNDARIO, false);
        p.rectangulo(MARGEN, 469, CONTENIDO, 85, 8, CLARO, BORDE);
        p.texto(43, 485, 700, "Cómo se pasa de grado", 13, AZUL, true);
        p.texto(43, 503, 700, "Al cierre del año se evalúan los niveles de logro por competencia.", 10, SECUNDARIO, false);
        p.texto(43, 522, 230, "Promoción", 10, AZUL, true);
        p.texto(43, 537, 230, "Cumple las condiciones de su grado.", 9, TINTA, false);
        p.texto(304, 522, 230, "Recuperación pedagógica", 10, AZUL, true);
        p.texto(304, 537, 230, "Refuerza las competencias pendientes.", 9, TINTA, false);
        p.texto(563, 522, 240, "Permanencia en el grado", 10, AZUL, true);
        p.texto(563, 537, 240, "Vuelve a cursar el grado cuando corresponde.", 9, TINTA, false);
        p.texto(MARGEN, 566, 600, "Fuente: planes guardados en AsisControl para el año seleccionado.", 9, SECUNDARIO, false);
        pie(p, numero, total);
        return p.pagina;
    }

    private void dibujarCurso(Pagina p, int x, int y, Fila fila, Tarjeta tarjeta) {
        p.rectangulo(x + 6, y, COLUMNA - 12, fila.alto() - 4, 4, tinte(fila.color()), null);
        p.rectangulo(x + 6, y + 4, 2, fila.alto() - 12, 0, fila.color(), null);
        int lineaY = y + 3;
        for (String linea : tarjeta.lineas()) {
            p.texto(x + 13, lineaY, COLUMNA - 26, linea, TAMANO_CURSO, TINTA, true);
            lineaY += INTERLINEADO;
        }
        String codigo = tarjeta.curso().codigo();
        float tamanoCodigo = Math.min(8, 103f / (float) REGULAR.getStringBounds(codigo, MEDICION).getWidth());
        p.texto(x + 13, y + fila.alto() - 18, 105, codigo, tamanoCodigo, SECUNDARIO, false);
        p.derecha(x + 116, y + fila.alto() - 19, 22, tarjeta.curso().horasSemanales() + " h", 9, fila.color(), true);
    }

    private JRBasePrintPage condiciones(JasperPrint documento, int total) {
        var p = new Pagina(documento);
        cabecera(p, "Condiciones para pasar de grado", "Promoción, recuperación y permanencia",
                "EDUCACIÓN BÁSICA REGULAR", "Secundaria", "");
        p.rectangulo(MARGEN, 124, CONTENIDO, 34, 6, CLARO, BORDE);
        p.texto(42, 134, 740, "AD · Logro destacado     A · Logro esperado     B · En proceso     C · En inicio", 11, AZUL, true);
        regla(p, MARGEN, "1.º, 3.º y 4.º", ACENTOS[0],
                "En cada área o taller, por lo menos la mitad de las competencias debe estar en B o en un nivel superior. En las demás puede haber AD, A o C.",
                "En cada una de cuatro o más áreas o talleres, más de la mitad de las competencias están en C.");
        regla(p, 428, "2.º y 5.º", ACENTOS[1],
                "En cada una de tres áreas o talleres, al menos la mitad de las competencias debe estar en A o AD. Para las competencias restantes se exige B.",
                "En cada una de cuatro o más áreas o talleres, la mitad o más de las competencias están en C.");
        p.rectangulo(MARGEN, 389, CONTENIDO, 153, 8, CLARO, BORDE);
        p.texto(44, 398, 735, "Recuperación pedagógica", 14, AZUL, true);
        p.texto(44, 421, 735, "Quiénes reciben recuperación", 11, TINTA, true);
        parrafo(p, 44, 439, 740, "Si al cierre del año el estudiante no califica para promoción ni para permanencia, recupera las competencias que quedaron en C.");
        p.texto(44, 467, 735, "Cómo se decide después de la recuperación", 11, TINTA, true);
        parrafo(p, 44, 484, 740, "Se aplican los criterios de promoción del grupo de grados correspondiente. Puede quedar una única área o un único taller con todas sus competencias en C para subsanarlas hasta diciembre del siguiente período lectivo. Si el estudiante no reúne las condiciones para promover, permanece en el grado.");
        var fuente = p.texto(MARGEN, 566, 600, "Fuente: RVM N.º 048-2024-MINEDU · Anexo I, numeral 5.1.3", 9, AZUL, true);
        fuente.setHyperlinkType(HyperlinkTypeEnum.REFERENCE);
        fuente.setHyperlinkReference(NORMA);
        pie(p, total, total);
        return p.pagina;
    }

    private void regla(Pagina p, int x, String grados, Color color, String promocion, String permanencia) {
        p.rectangulo(x, 171, 385, 203, 9, Color.WHITE, BORDE);
        p.rectangulo(x + 7, 178, 371, 44, 6, tinte(color), null);
        p.texto(x + 15, 182, 355, grados, 17, color, true);
        p.texto(x + 15, 203, 355, "Al cierre del año lectivo", 10, SECUNDARIO, false);
        p.texto(x + 15, 233, 355, "Promoción", 12, AZUL, true);
        parrafo(p, x + 15, 252, 355, promocion);
        p.rectangulo(x + 15, 304, 355, 1, 0, BORDE, null);
        p.texto(x + 15, 315, 355, "Permanencia en el grado", 12, AZUL, true);
        parrafo(p, x + 15, 334, 355, permanencia);
    }

    private void parrafo(Pagina p, int x, int y, int ancho, String texto) {
        for (String linea : envolver(texto, ancho, REGULAR, 11)) {
            p.texto(x, y, ancho, linea, 11, TINTA, false);
            y += 15;
        }
    }

    private void cabecera(Pagina p, String titulo, String subtitulo, String rotulo, String principal, String fechas) {
        p.rectangulo(MARGEN, 24, CONTENIDO, 86, 11, AZUL, null);
        p.rectangulo(615, 35, 186, 64, 8, PANEL_AZUL, null);
        p.texto(48, 38, 535, "ASISCONTROL / EDUCACIÓN SECUNDARIA", 10, BLANCO_AZULADO, true);
        p.texto(48, 54, 560, titulo, 25, Color.WHITE, true);
        p.texto(48, 85, 540, subtitulo, 12, BLANCO_AZULADO, false);
        p.derecha(625, 42, 164, rotulo, 8, BLANCO_AZULADO, true);
        p.derecha(625, 57, 164, principal, 21, Color.WHITE, true);
        p.derecha(625, 82, 164, fechas, 9, Color.WHITE, false);
    }

    private void pie(Pagina p, int pagina, int total) {
        p.derecha(743, 565, 70, pagina + " / " + total, 10, SECUNDARIO, true);
    }

    private static int columna(int numero) {
        return MARGEN + PASO * numero;
    }

    private static Color tinte(Color color) {
        return new Color((color.getRed() + 11 * 255) / 12,
                (color.getGreen() + 11 * 255) / 12, (color.getBlue() + 11 * 255) / 12);
    }

    private static Font fuente(String nombre) {
        try (var archivo = GeneradorPlanEstudios.class.getResourceAsStream("/net/sf/jasperreports/fonts/dejavu/" + nombre)) {
            return Font.createFont(Font.TRUETYPE_FONT, archivo);
        } catch (IOException | java.awt.FontFormatException e) {
            throw new IllegalStateException("No se pudo cargar la fuente del plan de estudios", e);
        }
    }

    private static List<String> envolver(String texto, int ancho, Font fuente, int tamano) {
        String limpio = texto.strip().replaceAll("\\s+", " ");
        var cadena = new AttributedString(limpio);
        cadena.addAttribute(TextAttribute.FONT, fuente.deriveFont((float) tamano));
        var medidor = new LineBreakMeasurer(cadena.getIterator(), MEDICION);
        var lineas = new ArrayList<String>();
        while (medidor.getPosition() < limpio.length()) {
            int inicio = medidor.getPosition();
            medidor.nextLayout(ancho - 2);
            lineas.add(limpio.substring(inicio, medidor.getPosition()).strip());
        }
        return lineas;
    }

    private record Tarjeta(CursoDelPlan curso, List<String> lineas) {}
    private record Fila(Tarjeta[] celdas, Color color, int alto) {}

    private static class Pagina {
        private final JasperPrint documento;
        private final JRBasePrintPage pagina = new JRBasePrintPage();

        Pagina(JasperPrint documento) {
            this.documento = documento;
        }

        void rectangulo(int x, int y, int ancho, int alto, int radio, Color fondo, Color borde) {
            var forma = new JRBasePrintRectangle(documento.getDefaultStyleProvider());
            forma.setX(x);
            forma.setY(y);
            forma.setWidth(ancho);
            forma.setHeight(alto);
            forma.setMode(ModeEnum.OPAQUE);
            forma.setRadius(radio);
            forma.setBackcolor(fondo);
            forma.getLinePen().setLineWidth(borde == null ? 0f : .8f);
            forma.getLinePen().setLineColor(borde);
            pagina.addElement(forma);
        }

        JRBasePrintText texto(int x, int y, int ancho, String valor, float tamano, Color color, boolean negrita) {
            var etiqueta = new JRBasePrintText(documento.getDefaultStyleProvider());
            etiqueta.setText(valor);
            etiqueta.setX(x);
            etiqueta.setY(y);
            etiqueta.setWidth(ancho);
            int alto = (int) Math.ceil(tamano * 1.25);
            etiqueta.setHeight(alto);
            etiqueta.setTextHeight(alto);
            etiqueta.setLineSpacingFactor(1f);
            etiqueta.setFontName(FUENTE);
            etiqueta.setFontSize(tamano);
            etiqueta.setBold(negrita);
            etiqueta.setForecolor(color);
            etiqueta.setLocaleCode(Locale.forLanguageTag("es-PE").toLanguageTag());
            pagina.addElement(etiqueta);
            return etiqueta;
        }

        void derecha(int x, int y, int ancho, String valor, float tamano, Color color, boolean negrita) {
            texto(x, y, ancho, valor, tamano, color, negrita).setHorizontalTextAlign(HorizontalTextAlignEnum.RIGHT);
        }
    }
}
