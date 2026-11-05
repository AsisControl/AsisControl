import { expect, test } from "vitest";
import { ApiError } from "@/core/api/client";
import { fieldErrorsOf, message } from "../task-error-copy";

test.each([
  "TAREA_NO_ENCONTRADA: id 42",
  "java.lang.IllegalStateException: persistencia",
  "Error SQL al actualizar /api/tareas/42",
  "La versión del servidor no coincide",
  "size must be between 1 and 1000",
])("No muestra detalles internos: %s", (text) => {
  expect(message(new ApiError(text, 422), "No se pudo guardar la tarea."))
    .toBe("No se pudo guardar la tarea.");
});

test("Conserva errores funcionales y presenta conexión y conflictos sin detalles técnicos", () => {
  const functional = "El puntaje máximo no puede ser menor que una nota ya registrada (18)";
  expect(message(new ApiError(functional, 422))).toBe(functional);
  expect(message(new Error("No se pudo conectar con el servidor. Comprueba que el backend esté iniciado.")))
    .toBe("No se pudo conectar. Revisa tu conexión e inténtalo de nuevo.");
  expect(message(new Error("El servidor tardó demasiado. Inténtalo nuevamente.")))
    .toBe("La solicitud tardó demasiado. Inténtalo de nuevo.");
  expect(message(new ApiError("version=7", 409, "VERSION_DESACTUALIZADA")))
    .toBe("Los datos cambiaron. Actualiza la información antes de guardar.");
});

test("Limpia la presentación por campo sin modificar el error ni sus datos", () => {
  const error = new ApiError("Error", 422, "DATOS_INVALIDOS", [
    { field: "nombre", message: "Escribe un nombre de hasta 150 caracteres." },
    { field: "motivo", message: "must not be blank: TAREA_MOTIVO_REQUERIDO" },
  ]);
  expect(fieldErrorsOf(error)).toEqual({
    nombre: "Escribe un nombre de hasta 150 caracteres.",
    motivo: "Revisa este campo antes de continuar.",
  });
  expect(error.code).toBe("DATOS_INVALIDOS");
  expect(error.fieldErrors[1].message).toBe("must not be blank: TAREA_MOTIVO_REQUERIDO");
});
