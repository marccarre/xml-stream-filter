package com.carmatechnologies.utilities.xml;

import com.carmatechnologies.utilities.xml.common.InputStreams;
import com.carmatechnologies.utilities.xml.common.MutablePair;
import com.carmatechnologies.utilities.xml.common.OutputStreams;
import com.carmatechnologies.utilities.xml.common.Pair;
import com.carmatechnologies.utilities.xml.common.XMLInputFactoryImpl;
import com.carmatechnologies.utilities.xml.transformer.XMLStreamReaderToDomTreeTransformer;
import com.google.common.io.Closeables;
import org.w3c.dom.Node;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.TransformerConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.carmatechnologies.utilities.xml.common.InputStreams.autoGUnzip;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@code XmlStreamFilter} allows you to:
 * - stream-process large files, but  nevertheless
 * - filter elements with a specific name, or validating the condition specified by the provided predicate, and
 * - transform filtered elements according to the provided transformer.
 */
public final class XmlStreamFilter implements StreamFilter {
    public static final String VERSION = "1.0";

    private final String elementLocalName;
    private final Predicate<Node> filter;
    private final Function<Pair<Node, OutputStream>, Void> transformer;
    private final XMLInputFactory xmlInputFactory;
    private final Function<XMLStreamReader, Node> domTreeTransformer;

    public XmlStreamFilter(final String elementLocalName, final Predicate<Node> filter, final Function<Pair<Node, OutputStream>, Void> transformer) throws TransformerConfigurationException {
        this(elementLocalName, filter, transformer, XMLInputFactoryImpl.newInstance(), new XMLStreamReaderToDomTreeTransformer());
    }

    public XmlStreamFilter(final String elementLocalName, final Predicate<Node> filter, final Function<Pair<Node, OutputStream>, Void> transformer, final XMLInputFactory xmlInputFactory, final Function<XMLStreamReader, Node> domTreeTransformer) throws TransformerConfigurationException {
        checkNotNull(elementLocalName, "XML element's local name must NOT be null.");
        checkArgument(!elementLocalName.isEmpty(), "XML element's local name must NOT be empty.");
        this.elementLocalName = elementLocalName;
        this.filter = checkNotNull(filter, "Filter must NOT be null.");
        this.transformer = checkNotNull(transformer, "Transformer must NOT be null.");
        this.xmlInputFactory = checkNotNull(xmlInputFactory, "XMLInputFactory must NOT be null.");
        this.domTreeTransformer = checkNotNull(domTreeTransformer, "XMLStreamReader-to-DOM tree transformer must NOT be null.");
    }

    @Override
    public void filter(InputStream rawInput, OutputStream rawOutput) throws XMLStreamException, IOException {
        checkNotNull(rawInput, "InputStream must NOT be null.");
        checkNotNull(rawOutput, "OutputStream must NOT be null.");

        // Improve stream processing's performance, and automatically gunzip where required.
        final InputStream in = autoGUnzip(InputStreams.buffered(rawInput));
        final OutputStream out = OutputStreams.buffered(rawOutput);

        final XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(in, UTF_8.name());
        final MutablePair<Node, OutputStream> outputHolder = MutablePair.withSecond(out);

        while (reader.hasNext()) {
            reader.next();
            while (isStartOfTargetElement(reader)) {
                final Node domTree = domTreeTransformer.apply(reader);
                if (filter.test(domTree)) {
                    transformer.apply(outputHolder.first(domTree));
                }
            }
        }

        closeQuietly(reader, out, in);
    }


    private boolean isStartOfTargetElement(final XMLStreamReader reader) throws XMLStreamException {
        return (reader.getEventType() == XMLEvent.START_ELEMENT) && elementLocalName.equals(reader.getLocalName());
    }

    private static void closeQuietly(final XMLStreamReader reader, final OutputStream out, final InputStream in) {
        closeQuietly(reader);
        closeQuietly(out);
        Closeables.closeQuietly(in);
    }

    private static void closeQuietly(final OutputStream out) {
        try {
            out.flush();
            out.close();
        } catch (IOException e) {
            // Voluntarily swallowed: nothing else to do at the end of the processing.
        }
    }

    private static void closeQuietly(final XMLStreamReader reader) {
        try {
            reader.close();
        } catch (XMLStreamException e) {
            // Voluntarily swallowed: nothing else to do at the end of the processing.
        }
    }

    public static void main(final String[] args) throws IOException, XMLStreamException {
        final StreamFilter streamFilter = new XmlStreamFilterCliFactory().newStreamFilter(args);
        streamFilter.filter(System.in, System.out);
    }
}
