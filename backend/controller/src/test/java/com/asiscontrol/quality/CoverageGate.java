package com.asiscontrol.quality;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.*;

/** Verificación del reporte agregado real, incluidas todas las capas del reactor. */
public final class CoverageGate {
    private CoverageGate() {}

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]);
        double minimum = Double.parseDouble(args[1]);
        if (minimum < 0 || minimum > 1) throw new IllegalArgumentException("El umbral debe estar entre 0 y 1");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        Element root = factory.newDocumentBuilder().parse(report.toFile()).getDocumentElement();
        boolean meets = true;
        StringBuilder summary = new StringBuilder("# Cobertura global del backend\n\n");
        summary.append("Incluye entity, dto, repository, service y controller, sin exclusiones de clases.\n\n");
        for (String type : List.of("INSTRUCTION", "LINE", "BRANCH")) {
            Element counter = counter(root, type);
            long covered = Long.parseLong(counter.getAttribute("covered"));
            long missed = Long.parseLong(counter.getAttribute("missed"));
            double ratio = covered + missed == 0 ? 1 : (double) covered / (covered + missed);
            String line = String.format(Locale.ROOT, "%s: %.2f%% (%d/%d)%n", type, ratio*100, covered,covered+missed);
            System.out.print(line); summary.append("- ").append(line);
            if (!type.equals("BRANCH") && ratio < minimum) meets = false;
        }
        summary.append(String.format(Locale.ROOT,"\nUmbral exigido: %.0f%% de instrucciones y líneas. Resultado: %s.\n",minimum*100,meets?"CUMPLE":"NO CUMPLE"));
        Files.writeString(report.resolveSibling("coverage-summary.md"), summary.toString());
        if (!meets) throw new IllegalStateException("La cobertura GLOBAL no alcanza el umbral; revise jacoco-aggregate/index.html");
    }

    private static Element counter(Element parent,String type) {
        for (Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())
            if(node instanceof Element e && e.getTagName().equals("counter") && e.getAttribute("type").equals(type)) return e;
        throw new IllegalStateException("El reporte no contiene el contador " + type);
    }
}
