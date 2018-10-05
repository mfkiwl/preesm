package org.ietr.preesm.experiment.model.pimm.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;

/**
 *
 * @author anmorvan
 *
 */
public class PiSDFXSDValidator {

  final URL schemaURL;

  private PiSDFXSDValidator() {
    schemaURL = new URLResolver(this.getClass().getClassLoader()).resolve("PiSDF.xsd", Collections.emptyList());
  }

  /**
   *
   */
  public static class PiSDFXSDValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PiSDFXSDValidationException(final String message) {
      super(message);
    }

    public PiSDFXSDValidationException(final String message, final Throwable cause) {
      super(message, cause);
    }

  }

  public static final void validate(final InputStream pisdfStreamed) throws IOException {
    new PiSDFXSDValidator().validateLocal(pisdfStreamed);
  }

  public static final void validate(final URL pisdfURL) throws IOException {
    validate(pisdfURL.openStream());
  }

  public static final void validate(final String pisdfcontent) throws IOException {
    final InputStream targetStream = new ByteArrayInputStream(pisdfcontent.getBytes());
    validate(targetStream);
  }

  /**
   * @throws IOException
   *
   */
  private final void validateLocal(final InputStream pisdfStreamed) throws IOException {
    if (this.schemaURL == null) {
      throw new NullPointerException("PiSDF XSD was not initialized properly");
    }
    pisdfStreamed.mark(Integer.MAX_VALUE);
    final Source xmlFile = new StreamSource(pisdfStreamed);
    final SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try {
      final Schema schema = schemaFactory.newSchema(schemaURL);
      final Validator validator = schema.newValidator();
      validator.validate(xmlFile);
    } catch (final SAXException e) {
      throw new PiSDFXSDValidationException("Could not validate PiSDF", e);
    }
    pisdfStreamed.reset();
  }
}
