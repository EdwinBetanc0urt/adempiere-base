# SP099 — Motor de Cálculo de Nómina por Expresión

## Resumen

Implementación de un motor de cálculo embebido para conceptos de nómina de tipo
**Expresión** (`CalculationType = 'E'`), más correcciones al evaluador para que
funcione correctamente dentro del `ParallelEngine`.

---

## Cambios incluidos

### Nuevas clases / archivos

| Archivo | Descripción |
|---|---|
| `org/spin/hr/util/PayrollCalculationEngine.java` | Punto de entrada al motor; invocado desde `MHRProcess` cuando `CalculationType = 'E'` |
| `org/spin/hr/util/PayrollExpressionEvaluator.java` | Parser/evaluador de expresiones (recursive-descent); soporta aritmética, condicionales, funciones y resolución de conceptos |

### Cambios en clases existentes

| Archivo | Cambio |
|---|---|
| `org/eevolution/hr/model/MHRProcess.java` | Delega a `PayrollCalculationEngine.calculate()` cuando `CalculationTypeValue = 'E'` |
| `org/spin/eca59/engine/EngineHelper.java` | Nuevo método `getConceptValue(String)` en la interfaz |
| `org/spin/eca59/engine/parallel/ParallelHelper.java` | Implementación de `getConceptValue(String)` — resuelve el concepto vía `ParallelEngine.movements` y retorna el valor tipado (`BigDecimal` / `String` / `Timestamp`) |

### Correcciones de bugs

#### Bug 1 — Expresión `SD * I_BR` retorna 0 en ParallelEngine

**Causa:** `PayrollExpressionEvaluator.parsePrimary()` resolvía identificadores
desconocidos (ej. `SD`) llamando a `process.getConceptValue()`, que lee de
`MHRProcess.movements`. Durante la ejecución de `ParallelEngine` ese mapa está
vacío; los movimientos calculados viven en `ParallelEngine.movements`.

**Fix:** El evaluador ahora prefiere `ruleContext.getEngineHelper().getConceptValue()`
(lee de `ParallelEngine.movements`) cuando `ruleContext` está en el contexto.
`process.getConceptValue()` queda como fallback para la ruta no-paralela.

#### Bug 2 — `EMPLOYEE_MONTHLY_SALARY` / `EMPLOYEE_DAILY_SALARY` retornan null

**Causas:**
1. El evaluador buscaba `getMonthlySalary(String)` (firma incorrecta; el método
   no tiene parámetros) → `NoSuchMethodException` silencioso → `null`.
2. Ambas constantes llamaban únicamente al objeto `process`, que en `ParallelEngine`
   no es `ParallelHelper`. Los métodos del helper nunca eran alcanzados.

**Fix:** Mismo patrón que el Bug 1 — preferir `ruleContext.getEngineHelper()`
(resuelve a `ParallelHelper`) con fallback al objeto `process`.

---

## Cómo usar

### Configuración en la ventana Concepto de Nómina

1. Crear o editar un concepto de tipo **Expresión**.
2. En el campo **Tipo de Cálculo** seleccionar `Expresión`.
3. En el campo **Expresión** ingresar la fórmula (ver sintaxis abajo).

### Ejemplos de expresiones

```
-- Multiplicación simple de dos conceptos
SD * I_BR

-- Salario diario por días del período
EMPLOYEE_DAILY_SALARY * _Days

-- Condicional: si el empleado tiene más de 5 años, 10% del salario mensual; si no, 5%
IF(ANTIGUEDAD_ATTR > 5, EMPLOYEE_MONTHLY_SALARY * 0.10, EMPLOYEE_MONTHLY_SALARY * 0.05)

-- Redondear a 2 decimales
ROUND(SD * I_BR, 2)

-- Valor mínimo garantizado
MAX(SALARIO_BASE * 0.15, 500)

-- Concepto del período anterior
SD_LAST

-- Suma de todos los conceptos del tipo INGRESO
INGRESOS_TYPE_SUM

-- Suma de conceptos de la categoría BASE
BASE_CATEGORY_SUM

-- Atributo de antigüedad del empleado
ANTIGUEDAD_ATTR

-- Comisión del período actual
COMMISSION_AMT

-- Comisión por tipo de base
COMMISSION('S')

-- Días de asistencia en el período
ASISTENCIA_INCIDENCE_SUM

-- Expresión compuesta con función de fecha
IF(DATEDIFF(_DateStart, _To) > 365, EMPLOYEE_MONTHLY_SALARY / 12, 0)

-- NVL: usar atributo si existe, si no el salario mensual
NVL(BONO_ATTR, EMPLOYEE_MONTHLY_SALARY * 0.05)
```

---

## Variables disponibles en las expresiones

### Del proceso

| Variable | Tipo | Descripción |
|---|---|---|
| `_Process` | Integer | `HR_Process_ID` |
| `_Payroll` | Integer | `HR_Payroll_ID` |
| `_HR_Payroll_Value` | String | Código de la nómina |
| `_Department` | Integer | `HR_Department_ID` |
| `_Period` | Integer | Número de período |
| `_PeriodNo` | Integer | Número de período (alias) |
| `_HR_Period_ID` | Integer | `HR_Period_ID` |
| `_From` | Timestamp | Fecha inicio del período |
| `_To` | Timestamp | Fecha fin del período |

### Del empleado

| Variable | Tipo | Descripción |
|---|---|---|
| `_C_BPartner_ID` | Integer | ID del socio de negocio |
| `_HR_Employee_ID` | Integer | `HR_Employee_ID` |
| `_C_BPartner` | Object | Objeto `MBPartner` |
| `_HR_Employee` | Object | Objeto `MHREmployee` |
| `_DateStart` | Timestamp | Fecha de inicio del empleado |
| `_DateEnd` | Timestamp | Fecha de fin del empleado |
| `_Days` | Integer | Días del período (ValidFrom → ValidTo + 1) |
| `_HR_Employee_ValidFrom` | Timestamp | ValidFrom del vínculo empleado-nómina |
| `_HR_Employee_ValidTo` | Timestamp | ValidTo del vínculo empleado-nómina |
| `_HR_Employee_Payroll_Value` | String | Código de la nómina asignada al empleado |
| `_HR_Employee_Contract` | Object | Objeto `MHRContract` del empleado |

### Del concepto en cálculo

| Variable | Tipo | Descripción |
|---|---|---|
| `_HR_Concept_ID` | Integer | `HR_Concept_ID` del concepto que se está calculando |
| `_HR_Concept` | Object | Objeto `MHRConcept` |
| `_HR_PayrollConcept_ID` | Integer | `HR_PayrollConcept_ID` |

### Constantes de salario y comisión

| Variable | Tipo | Descripción |
|---|---|---|
| `EMPLOYEE_MONTHLY_SALARY` | BigDecimal | Salario mensual del empleado |
| `EMPLOYEE_DAILY_SALARY` | BigDecimal | Salario diario del empleado |
| `COMMISSION_AMT` | BigDecimal | Comisión del empleado en el período actual |

### Resolución dinámica de conceptos

Cualquier identificador en MAYÚSCULAS que no coincida con las variables anteriores
se resuelve como un **concepto de nómina** por su `Value`:

| Patrón | Ejemplo | Equivale a |
|---|---|---|
| `CONCEPTO` | `SD` | Valor numérico/texto/fecha del concepto actual |
| `CONCEPTO_LAST` | `SD_LAST` | Último valor del concepto en la nómina actual, de un período que terminó antes del actual |
| `CONCEPTO_ATTR` | `ANTIGUEDAD_ATTR` | Atributo del concepto (`getAttribute`) |
| `CONCEPTO_TYPE_SUM` | `INGRESOS_TYPE_SUM` | Suma de todos los conceptos del tipo |
| `CONCEPTO_GROUP_SUM` | `BASE_GROUP_SUM` | Suma por grupo (`getConceptGroup`) |
| `CONCEPTO_CATEGORY_SUM` | `BASE_CATEGORY_SUM` | Suma por categoría |
| `CONCEPTO_INCIDENCE_SUM` | `FALTAS_INCIDENCE_SUM` | Suma de incidencias en `_From`–`_To` |

---

## Funciones disponibles

### Numéricas

| Función | Descripción | Ejemplo |
|---|---|---|
| `ROUND(n, d)` | Redondea `n` a `d` decimales (HALF_UP) | `ROUND(SD * 0.1, 2)` |
| `ABS(n)` | Valor absoluto | `ABS(DESCUENTO)` |
| `FLOOR(n)` | Redondea hacia abajo (entero inferior) | `FLOOR(DIAS * 1.5)` |
| `CEIL(n)` | Redondea hacia arriba (entero superior) | `CEIL(HORAS / 8)` |
| `MIN(a, b)` | Menor de dos valores | `MIN(BONO, 1000)` |
| `MAX(a, b)` | Mayor de dos valores | `MAX(CALCULO, 500)` |

### Condicionales

| Función | Descripción | Ejemplo |
|---|---|---|
| `IF(cond, entonces, sino)` | Ternario | `IF(ANTIGUEDAD_ATTR > 1, SD * 0.1, 0)` |
| `COALESCE(a, b, ...)` | Primer valor no nulo | `COALESCE(BONO_ATTR, SD * 0.05)` |
| `NVL(val, defecto)` | `val` si no es nulo, si no `defecto` | `NVL(EXTRA, 0)` |

### Texto

| Función | Descripción | Ejemplo |
|---|---|---|
| `UPPER(s)` | Convierte a mayúsculas | `UPPER(NOMBRE)` |
| `LOWER(s)` | Convierte a minúsculas | `LOWER(CARGO)` |
| `TRIM(s)` | Elimina espacios al inicio/fin | `TRIM(CODIGO)` |
| `LENGTH(s)` | Longitud de la cadena | `LENGTH(DESCRIPCION)` |
| `CONCAT(a, b, ...)` | Concatena valores | `CONCAT('Período: ', _PeriodNo)` |
| `TOSTRING(v)` | Convierte a texto | `TOSTRING(SD)` |
| `TONUMBER(s)` | Convierte texto a número | `TONUMBER(CODIGO_ATTR)` |

### Fecha

| Función | Descripción | Ejemplo |
|---|---|---|
| `TODAY()` | Fecha y hora actual | `TODAY()` |
| `DATEDIFF(d1, d2)` | Días entre dos fechas | `DATEDIFF(_DateStart, _To)` |

### Histórico y comisiones

| Función | Argumentos | Descripción | Ejemplo |
|---|---|---|---|
| `LAST(concepto, nomina)` | String, String | Último valor del concepto en la nómina indicada, de un período que terminó antes del actual | `LAST('SD', 'NOM_MENS')` |
| `SAME_PERIOD(concepto, nomina)` | String, String | Valor del concepto en un proceso completado de la nómina indicada que cubre exactamente las mismas fechas del período actual; `null` si no hay | `SAME_PERIOD('A_GRAV_BPS', 'NOM_LICENCIA_SV')` |
| `COMMISSION(tipo)` | String | Comisión del empleado por tipo de base | `COMMISSION('S')` |
| `COMMISSION_RANGE(desde, hasta, tipo)` | Timestamp, Timestamp, String | Comisión en rango de fechas | `COMMISSION_RANGE(_From, _To, 'S')` |

### Literales especiales

| Literal | Descripción |
|---|---|
| `TRUE` / `FALSE` | Valores booleanos |
| `NULL` | Valor nulo |

---

## Operadores soportados

| Operador | Descripción |
|---|---|
| `+` `-` `*` `/` `%` | Aritmética básica; `+` concatena strings si alguno es texto |
| `==` `=` `!=` `<` `<=` `>` `>=` | Comparación |
| `AND` `OR` `NOT` | Lógica booleana |
| `( )` | Agrupación y llamadas a función |
| `-expr` | Negación unaria |

---

## Notas técnicas

- El evaluador funciona tanto en la ruta **secuencial** (`MHRProcess`) como en la
  **paralela** (`ParallelEngine`). En la ruta paralela los conceptos se resuelven
  desde `ParallelEngine.movements`, no desde `MHRProcess.movements` (que estaría vacío).
- Los valores numéricos retornados son siempre `BigDecimal`.
- Si un identificador no se puede resolver (concepto inexistente, atributo sin valor,
  etc.) se retorna `null`, que en operaciones numéricas se trata como `0`.
- El tipo de retorno del concepto resuelto depende de su `ColumnType`:
  - `Amount` / `Quantity` → `BigDecimal`
  - `Text` → `String`
  - `Date` → `Timestamp`
