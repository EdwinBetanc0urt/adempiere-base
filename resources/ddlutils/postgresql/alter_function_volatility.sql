-- Declares the volatility of the read-only PL/pgSQL functions of the ADempiere schema.
--
-- By default PostgreSQL treats every function as VOLATILE, which prevents the planner from flattening
-- views that call them (e.g. RV_Payment -> paymentAllocated/paymentAvailable, RV_C_InvoiceLine ->
-- productAttribute, RV_C_Invoice -> charAt), from filtering/limiting before joining, and from skipping
-- columns nobody selects. These functions only read data (no INSERT/UPDATE/DELETE, sequences, random
-- values or clock functions, directly or through the functions they call), so they can be STABLE;
-- charAt is a pure SUBSTR and can be IMMUTABLE.
--
-- Functions that write, use sequences or randomness (nextID*, getMaxTableID, getUUID, alterColumn,
-- deps_*, add_missing_translations) and extension (C) functions are intentionally left untouched.
--
-- Idempotent and safe to re-run; functions missing in a database are skipped. Only catalog metadata
-- changes (no data). To undo a function: ALTER FUNCTION <name>(<arg types>) VOLATILE;
DO $$
DECLARE
    r RECORD;
    f regprocedure;
BEGIN
    FOR r IN
        SELECT * FROM (VALUES
        ('charat', 'character varying, integer', 'IMMUTABLE'),
        ('acctbalance', 'numeric, numeric, numeric', 'STABLE'),
        ('actualproductprice', 'numeric, numeric', 'STABLE'),
        ('add_months', 'timestamp with time zone, numeric', 'STABLE'),
        ('adddays', 'interval, numeric', 'STABLE'),
        ('adddays', 'timestamp with time zone, numeric', 'STABLE'),
        ('addweeks', 'timestamp with time zone, numeric', 'STABLE'),
        ('addyears', 'timestamp with time zone, numeric', 'STABLE'),
        ('bompricelimit', 'numeric, numeric', 'STABLE'),
        ('bompricelimit', 'numeric, numeric, numeric', 'STABLE'),
        ('bompricelist', 'numeric, numeric', 'STABLE'),
        ('bompricelist', 'numeric, numeric, numeric', 'STABLE'),
        ('bompricestd', 'numeric, numeric', 'STABLE'),
        ('bompricestd', 'numeric, numeric, numeric', 'STABLE'),
        ('bomqtyavailable', 'numeric, numeric, numeric', 'STABLE'),
        ('bomqtyavailableasi', 'numeric, numeric, numeric, numeric', 'STABLE'),
        ('bomqtyonhand', 'numeric, numeric, numeric', 'STABLE'),
        ('bomqtyonhandasi', 'numeric, numeric, numeric, numeric', 'STABLE'),
        ('bomqtyordered', 'numeric, numeric, numeric', 'STABLE'),
        ('bomqtyorderedasi', 'numeric, numeric, numeric, numeric', 'STABLE'),
        ('bomqtyreserved', 'numeric, numeric, numeric', 'STABLE'),
        ('bomqtyreservedasi', 'numeric, numeric, numeric, numeric', 'STABLE'),
        ('bpartnerremitlocation', 'numeric', 'STABLE'),
        ('currencybase', 'numeric, numeric, timestamp with time zone, numeric, numeric', 'STABLE'),
        ('currencybase', 'numeric, numeric, timestamp with time zone, numeric, numeric, numeric', 'STABLE'),
        ('currencyconvert', 'numeric, numeric, numeric, timestamp with time zone, numeric, numeric, numeric', 'STABLE'),
        ('currencyconvert_receipt', 'numeric, numeric, numeric, numeric, numeric, numeric', 'STABLE'),
        ('currencyrate', 'numeric, numeric, timestamp with time zone, numeric, numeric, numeric', 'STABLE'),
        ('currencyround', 'numeric, numeric, character varying', 'STABLE'),
        ('dailysalary', 'numeric', 'STABLE'),
        ('dailysalarytodate', 'numeric, timestamp with time zone', 'STABLE'),
        ('dailysalarytodatebyhrprocess', 'numeric, numeric, timestamp with time zone', 'STABLE'),
        ('daysbetween', 'timestamp with time zone, timestamp with time zone', 'STABLE'),
        ('documentno', 'numeric', 'STABLE'),
        ('duedate_deferreddoc', 'numeric', 'STABLE'),
        ('f_convnl', 'numeric', 'STABLE'),
        ('financialratetodate', 'numeric, timestamp with time zone', 'STABLE'),
        ('firstinvoicefororder', 'numeric', 'STABLE'),
        ('firstof', 'timestamp with time zone, character varying', 'STABLE'),
        ('get_all_views', 'character varying', 'STABLE'),
        ('get_sysconfig', 'character varying, character varying, numeric, numeric', 'STABLE'),
        ('getdate', '', 'STABLE'),
        ('invoicediscount', 'numeric, timestamp with time zone, numeric', 'STABLE'),
        ('invoicefororder', 'numeric', 'STABLE'),
        ('invoiceopen', 'numeric, numeric', 'STABLE'),
        ('invoiceopentodate', 'numeric, numeric, date', 'STABLE'),
        ('invoiceopentodate', 'numeric, numeric, timestamp with time zone', 'STABLE'),
        ('invoicepaid', 'numeric, numeric, numeric', 'STABLE'),
        ('invoicepaidtodate', 'numeric, numeric, numeric, timestamp with time zone', 'STABLE'),
        ('linenetamtrealinvoiceline', 'numeric', 'STABLE'),
        ('linenetamtrealorderline', 'numeric', 'STABLE'),
        ('maxpaydate', 'numeric', 'STABLE'),
        ('monthlysalary', 'numeric', 'STABLE'),
        ('monthlysalarytodate', 'numeric, timestamp with time zone', 'STABLE'),
        ('nextbusinessday', 'timestamp with time zone, numeric', 'STABLE'),
        ('paymentallocated', 'numeric, numeric', 'STABLE'),
        ('paymentavailable', 'numeric', 'STABLE'),
        ('paymentavailabletodate', 'numeric, timestamp with time zone', 'STABLE'),
        ('paymenttermdiscount', 'numeric, numeric, numeric, timestamp with time zone, timestamp with time zone', 'STABLE'),
        ('paymenttermduedate', 'numeric, timestamp with time zone', 'STABLE'),
        ('paymenttermduedays', 'numeric, timestamp with time zone, timestamp with time zone', 'STABLE'),
        ('payreceiptavailable', 'numeric, character varying', 'STABLE'),
        ('payreceiptavailabletodate', 'numeric, character varying, timestamp with time zone', 'STABLE'),
        ('processreportsource', 'numeric, numeric, numeric, timestamp without time zone, timestamp without time zone', 'STABLE'),
        ('prodqtyonhand', 'numeric, numeric', 'STABLE'),
        ('prodqtyordered', 'numeric, numeric', 'STABLE'),
        ('prodqtyreserved', 'numeric, numeric', 'STABLE'),
        ('prodqtyreserved', 'numeric, numeric, integer', 'STABLE'),
        ('productattribute', 'numeric', 'STABLE'),
        ('round', 'numeric, numeric', 'STABLE'),
        ('sp005_productpricelastchange', 'numeric, numeric, numeric, timestamp without time zone', 'STABLE'),
        ('sp005_taxrateat', 'numeric, timestamp without time zone, character varying', 'STABLE'),
        ('sp008_get_account_tree', 'numeric', 'STABLE'),
        ('sp013_taxrateat', 'numeric, timestamp without time zone, character varying', 'STABLE'),
        ('sp028c_invoiceopen', 'numeric, numeric', 'STABLE'),
        ('sp028c_invoiceopentodate', 'numeric, numeric, timestamp with time zone', 'STABLE'),
        ('sp028c_invoiceopentodate_convert', 'numeric, numeric, timestamp with time zone, numeric', 'STABLE'),
        ('sp028c_invoicepaidtodate', 'numeric, numeric, numeric, timestamp with time zone', 'STABLE'),
        ('sp028c_paymentavailable', 'numeric', 'STABLE'),
        ('sp028c_payreceiptavailabletodate', 'numeric, character varying, timestamp with time zone', 'STABLE'),
        ('subtractdays', 'interval, numeric', 'STABLE'),
        ('subtractdays', 'timestamp with time zone, numeric', 'STABLE'),
        ('taxamt_notinvoiced', 'numeric', 'STABLE'),
        ('taxamtreal', 'numeric', 'STABLE'),
        ('trunc', 'interval', 'STABLE'),
        ('trunc', 'timestamp with time zone', 'STABLE'),
        ('trunc', 'timestamp with time zone, character varying', 'STABLE'),
        ('unaccent_string', 'text', 'STABLE'),
        ('unaccent_string', 'text, numeric', 'STABLE'),
        ('unallocatedpaymentamount', 'numeric, timestamp with time zone', 'STABLE'),
        ('unallocatedpaymentamountconvert', 'numeric, timestamp with time zone', 'STABLE')
        ) AS t(fname, argtypes, volatility)
    LOOP
        f := to_regprocedure(format('%I(%s)', r.fname, r.argtypes));
        IF f IS NOT NULL THEN
            EXECUTE format('ALTER FUNCTION %s %s', f, r.volatility);
        END IF;
    END LOOP;
END
$$;
