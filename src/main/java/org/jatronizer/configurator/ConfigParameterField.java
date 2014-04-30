package org.jatronizer.configurator;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;

/**
 * {@code ConfigParameterField} represents one of the parameters managed by a {@code InstanceConfigurator}.
 * It provides methods to get and set values of the parameter and to access metadata,
 * e.g. the parameter type and available values if it's an enum.
 * @param <C> Type of the configure.
 * @param <P> Type of the configure parameter.
 */
final class ConfigParameterField<C,P> implements ConfigParameter<C,P> {

	public final String key;
	public final String defaultValue;
	public final String description;
	private final Converter<P> converter;
	private final Field field;
	private final String[] enumNames;
	private final Field[] enumFields;

	static <C,P> ConfigParameterField<C,P> create(
			C configuration, Field field, String key,
			Class<P> converterClass, String keyPrefix) {
		String description = ConfigSupport.description(field);
		if (keyPrefix == null) {
			keyPrefix = "";
		}
		if (key == null || "".equals(key)) {
			key = field.getName();
		}
		key = keyPrefix + key;
		Converter<P> converter = null;
		if (converterClass == null || converterClass == DefaultConverters.NullConverter.class) {
			converter = DefaultConverters.getFor((Class<P>) field.getType());
		}
		String defaultValue;
		if (converter == null) {
			try {
				converter = (Converter<P>) converterClass.newInstance();
			} catch (Exception e) {
				throw new ConfigurationException(e);
			}
		}
		String[] enumValues = new String[0];
		Field[] enumFields = new Field[0];
		Class c = field.getType();
		if (c.isEnum()) {
			try {
				Field[] fields = c.getDeclaredFields();
				int i = 0;
				for (int j = 0; j < fields.length; j++) {
					if (fields[j].isEnumConstant()) {
						fields[i] = fields[j];
						i++;
					}
				}
				enumFields = new Field[i];
				System.arraycopy(fields, 0, enumFields, 0, enumFields.length);
				if (enumFields.length > 0 && !enumFields[0].isAccessible()) {
					// NOTE making the enum constants accessible is not reverted later.
					AccessibleObject.setAccessible(enumFields, true);
				}
				// sort alphabetically by name to enable binarySearch
				Arrays.sort(enumFields, new Comparator<Field>() {
					public int compare(Field o1, Field o2) {
						return o1.getName().compareTo(o2.getName());
					}
				});
				enumValues = new String[enumFields.length];
				for (int j = 0; j < i; j++) {
					enumValues[j] = enumFields[j].getName();
				}
			} catch (Exception e) {
				throw new ConfigurationException("Could not access enum values of " + c, e);
			}
		}
		synchronized (configuration) {
			P value;
			try {
				if (!field.isAccessible()) {
					// NOTE making field accessible is not reverted later.
					field.setAccessible(true);
				}
				value = (P) field.get(configuration);
			} catch (Exception e) {
				throw new ConfigurationException(field.toString() + " could not be accessed", e);
			}
			defaultValue = converter.toString(value);
		}
		return new ConfigParameterField<C,P>(
				key,
				field,
				defaultValue,
				description,
				converter,
				enumValues,
				enumFields
		);
	}

	private ConfigParameterField(
			String key, Field field, String defaultValue,
			String description, Converter<P> converter,
			String[] enumNames, Field[] enumFields) {
		this.key = key;
		this.defaultValue = defaultValue;
		this.description = description;
		this.field = field;
		this.converter = converter;
		this.enumNames = enumNames;
		this.enumFields = enumFields;
	}

	/**
	 * {@inheritDoc}
	 */
	public String key() {
		return key;
	}

	/**
	 * {@inheritDoc}
	 */
	public String defaultValue() {
		return defaultValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public String description() {
		return description;
	}

	/**
	 * {@inheritDoc}
	 */
	public String get(C configuration) {
		try {
			P value;
			synchronized (configuration) {
				value = (P) field.get(configuration);
			}
			return converter.toString(value);
		} catch (IllegalValueException ie) {
			throw ie;
		} catch (Exception e) {
			throw new IllegalValueException("could not get the value of " + field, e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void set(C configuration, String value) {
		try {
			P v = converter.fromString(value);
			synchronized (configuration) {
				field.set(configuration, v);
			}
		} catch (IllegalValueException ie) {
			throw ie;
		} catch (Exception e) {
			throw new IllegalValueException("could not set the value of " + field, e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public Class<P> type() {
		return (Class<P>) field.getType();
	}

	/**
	 * {@inheritDoc}
	 */
	public P fromString(String value) {
		return converter.fromString(value);
	}

	/**
	 * {@inheritDoc}
	 */
	public String toString(P value) {
		return converter.toString(value);
	}

	/**
	 * {@inheritDoc}
	 */
	public String[] options() {
		return enumNames.clone();
	}

	/**
	 * {@inheritDoc}
	 */
	public String description(String option) {
		Field field = enumField(option);
		if (field == null) {
			return null;
		}
		return ConfigSupport.description(field);
	}

	/**
	 * Retrieves the enum Field with the specified name.
	 * If the parameter type is not an enum, {@code null} is returned.
	 */
	public Field enumField(String name) {
		int i = Arrays.binarySearch(enumNames, name);
		if (i < 0) {
			return null;
		}
		return enumFields[i];
	}

	/**
	 * {@inheritDoc}
	 */
	public Class<C> outerType() {
		return (Class<C>) field.getDeclaringClass();
	}

	/**
	 * {@inheritDoc}
	 */
	public String toString() {
		return key + " (" + defaultValue + "): " + description;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other.getClass() != ConfigParameterField.class) {
			return false;
		}
		ConfigParameterField<C,P> opf = (ConfigParameterField<C,P>) other;
		return
				key.equals(opf.key) &&
				field.equals(opf.field) &&
				converter.equals(opf.hashCode())
		;
	}

	/**
	 * {@inheritDoc}
	 */
	public int hashCode() {
		return (key.hashCode() ^ ~field.hashCode()) + converter.hashCode();
	}
}
