package org.exist.xquery.functions.fn;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;

/**
 * Functions related to JSON parsing.
 *
 * @author Wolf
 */
public class JSON extends BasicFunction {

    public static final FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("parse-json", Function.BUILTIN_FUNCTION_NS),
                "Parses a string supplied in the form of a JSON text, returning the results typically in the form of a map or array.",
                new SequenceType[]{
                        new FunctionParameterSequenceType("json-text", Type.STRING, Cardinality.ZERO_OR_ONE, "JSON string")
                },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "The parsed data, typically a map, array or atomic value")
        ),
        new FunctionSignature(
                new QName("parse-json", Function.BUILTIN_FUNCTION_NS),
                "Parses a string supplied in the form of a JSON text, returning the results typically in the form of a map or array.",
                new SequenceType[]{
                        new FunctionParameterSequenceType("json-text", Type.STRING, Cardinality.ZERO_OR_ONE, "JSON string"),
                        new FunctionParameterSequenceType("options", Type.MAP, Cardinality.EXACTLY_ONE, "Parsing options")
                },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "The parsed data, typically a map, array or atomic value")
        )
    };

    public final static String OPTION_DUPLICATES = "duplicates";
    public final static String OPTION_DUPLICATES_REJECT = "reject";
    public final static String OPTION_DUPLICATES_USE_FIRST = "use-first";
    public final static String OPTION_DUPLICATES_USE_LAST = "use-last";
    public final static String OPTION_LIBERAL = "liberal";
    public final static String OPTION_UNESCAPE = "unescape";

    public JSON(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        // process options if present
        // TODO: jackson does not allow access to raw string, so option "unescape" is not supported
        boolean liberal = false;
        String handleDuplicates = OPTION_DUPLICATES_USE_LAST;
        if (getArgumentCount() == 2) {
            final MapType options = (MapType)args[1].itemAt(0);
            final Sequence liberalOpt = options.get(new StringValue(OPTION_LIBERAL));
            if (liberalOpt.hasOne()) {
                liberal = liberalOpt.itemAt(0).convertTo(Type.BOOLEAN).effectiveBooleanValue();
            }
            final Sequence duplicateOpt = options.get(new StringValue(OPTION_DUPLICATES));
            if (duplicateOpt.hasOne()) {
                handleDuplicates = duplicateOpt.itemAt(0).getStringValue();
            }
        }

        JsonFactory factory = new JsonFactory();
        factory.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);
        // duplicates are handled in readValue
        factory.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, false);
        if (liberal) {
            factory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            factory.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            factory.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
            factory.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            factory.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        }

        try {
            JsonParser parser = factory.createParser(args[0].getStringValue());

            Item result = readValue(context, parser, handleDuplicates);
            return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOJS0001, e.getMessage());
        } catch (XPathException e) {
            e.setLocation(getLine(), getColumn(), getSource());
            throw e;
        }
    }

    /**
     * Generate an XDM from the tokens delivered by the JSON parser.
     *
     * @param context the XQueryContext
     * @param parser parser to use
     * @param handleDuplicates string indicating how to handle duplicate property names
     * @return the top item read
     * @throws IOException
     * @throws XPathException
     */
    public static Item readValue(XQueryContext context, JsonParser parser, String handleDuplicates) throws IOException, XPathException {
        return readValue(context, parser, null, handleDuplicates);
    }

    private static Item readValue(XQueryContext context, JsonParser parser, Item parent, String handleDuplicates) throws IOException, XPathException {
        JsonToken token;
        Item next = null;
        while ((token = parser.nextValue()) != null) {
            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                return parent;
            }
            switch (token) {
                case START_OBJECT:
                    next = new MapType(context, null);
                    readValue(context, parser, next, handleDuplicates);
                    break;
                case START_ARRAY:
                    next = new ArrayType(context, Sequence.EMPTY_SEQUENCE);
                    readValue(context, parser, next, handleDuplicates);
                    break;
                case VALUE_FALSE:
                    next = BooleanValue.FALSE;
                    break;
                case VALUE_TRUE:
                    next = BooleanValue.TRUE;
                    break;
                case VALUE_NUMBER_FLOAT:
                case VALUE_NUMBER_INT:
                    // according to spec, all numbers are converted to double
                    next = new StringValue(parser.getText()).convertTo(Type.DOUBLE);
                    break;
                case VALUE_NULL:
                    break;
                default:
                    next = new StringValue(parser.getText());
                    break;
            }
            if (parent != null) {
                switch (parent.getType()) {
                    case Type.ARRAY:
                        ((ArrayType)parent).add(next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence());
                        break;
                    case Type.MAP:
                        final String currentName = parser.getCurrentName();
                        if (currentName == null) {
                            throw new XPathException(ErrorCodes.FOJS0001, "Invalid JSON object");
                        }
                        final StringValue name = new StringValue(currentName);
                        final MapType map = (MapType) parent;
                        if (map.contains(name)) {
                            // handle duplicate keys
                            if (handleDuplicates.equals(OPTION_DUPLICATES_REJECT)) {
                                throw new XPathException(ErrorCodes.FOJS0003, "Duplicate key: " + currentName);
                            }
                            if (handleDuplicates.equals(OPTION_DUPLICATES_USE_LAST)) {
                                map.add(name, next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence());
                            }
                        } else {
                            map.add(name, next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence());
                        }
                        break;
                }
            }
        }
        return next;
    }
}
