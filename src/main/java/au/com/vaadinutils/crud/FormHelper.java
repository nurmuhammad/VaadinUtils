package au.com.vaadinutils.crud;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import au.com.vaadinutils.crud.splitFields.SplitCheckBox;
import au.com.vaadinutils.crud.splitFields.SplitComboBox;
import au.com.vaadinutils.crud.splitFields.SplitDateField;
import au.com.vaadinutils.crud.splitFields.SplitLabel;
import au.com.vaadinutils.crud.splitFields.SplitTextArea;
import au.com.vaadinutils.crud.splitFields.SplitTextField;

import com.google.common.base.Preconditions;
import com.vaadin.addon.jpacontainer.JPAContainer;
import com.vaadin.addon.jpacontainer.JPAContainerFactory;
import com.vaadin.addon.jpacontainer.fieldfactory.SingleSelectConverter;
import com.vaadin.data.Container;
import com.vaadin.data.util.IndexedContainer;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.AbstractLayout;
import com.vaadin.ui.AbstractSelect.ItemCaptionMode;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.DateField;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;

public class FormHelper<E> implements Serializable
{
	private static final long serialVersionUID = 1L;

	ArrayList<AbstractComponent> fieldList = new ArrayList<AbstractComponent>();
	private AbstractLayout form;
	private ValidatingFieldGroup<E> group;

	public FormHelper(AbstractLayout form, ValidatingFieldGroup<E> group)
	{
		this.form = form;
		this.group = group;
	}

	public TextField bindTextField(String fieldLabel, String fieldName)
	{
		TextField field = bindTextField(form, group, fieldLabel, fieldName);
		this.fieldList.add(field);
		return field;
	}

	public TextField bindTextField(AbstractLayout form, ValidatingFieldGroup<E> group, String fieldLabel,
			String fieldName)
	{
		TextField field = new SplitTextField(fieldLabel);
		field.setWidth("100%");
		field.setImmediate(true);
		field.setNullRepresentation("");
		field.setNullSettingAllowed(false);
		if (group != null)
			group.bind(field, fieldName);
		form.addComponent(field);
		return field;
	}

	public TextArea bindTextAreaField(String fieldLabel, String fieldName, int rows)
	{
		TextArea field = bindTextAreaField(form, group, fieldLabel, fieldName, rows);
		this.fieldList.add(field);
		return field;
	}

	public TextArea bindTextAreaField(AbstractLayout form, ValidatingFieldGroup<E> group, String fieldLabel,
			String fieldName, int rows)
	{
		TextArea field = new SplitTextArea(fieldLabel);
		field.setRows(rows);
		field.setWidth("100%");
		field.setImmediate(true);
		field.setNullRepresentation("");
		if (group != null)
			group.bind(field, fieldName);
		form.addComponent(field);
		return field;
	}

	public DateField bindDateField(String fieldLabel, String fieldName)
	{
		DateField field = bindDateField(form, group, fieldLabel, fieldName);
		this.fieldList.add(field);
		return field;
	}

	public DateField bindDateField(AbstractLayout form, ValidatingFieldGroup<E> group, String fieldLabel,
			String fieldName)
	{
		DateField field = new SplitDateField(fieldLabel);
		field.setDateFormat("yyyy-MM-dd");

		field.setImmediate(true);
		field.setWidth("100%");
		if (group != null)
			group.bind(field, fieldName);
		form.addComponent(field);
		return field;
	}

	public Label bindLabel(String fieldLabel)
	{
		Label field = bindLabel(form, group, fieldLabel);
		this.fieldList.add(field);
		return field;
	}

	public Label bindLabel(AbstractLayout form, ValidatingFieldGroup<E> group, String fieldLabel)
	{
		Label field = new SplitLabel(fieldLabel);
		field.setWidth("100%");
		form.addComponent(field);
		return field;
	}

	public Label bindLabel(AbstractLayout form, ValidatingFieldGroup<E> group, Label field)
	{
		field.setWidth("100%");
		form.addComponent(field);
		return field;
	}

	public ComboBox bindEnumField(String fieldLabel, String fieldName, Class<?> clazz)
	{
		ComboBox field = bindEnumField(form, group, fieldLabel, fieldName, clazz);
		this.fieldList.add(field);
		return field;
	}

	public ComboBox bindEnumField(AbstractLayout form, ValidatingFieldGroup<E> group, String fieldLabel,
			String fieldName, Class<?> clazz)
	{
		ComboBox field = new SplitComboBox(fieldLabel, createContainerFromEnumClass(fieldName, clazz));
		field.setNewItemsAllowed(false);
		field.setNullSelectionAllowed(false);
		field.setTextInputAllowed(false);
		field.setWidth("100%");
		field.setImmediate(true);
		if (group != null)
			group.bind(field, fieldName);
		form.addComponent(field);
		return field;
	}

	public CheckBox bindBooleanField(String fieldLabel, String fieldName)
	{
		CheckBox field = bindBooleanField(form, group, fieldLabel, fieldName);
		this.fieldList.add(field);
		return field;
	}

	public CheckBox bindBooleanField(AbstractLayout form, ValidatingFieldGroup<E> group, String fieldLabel,
			String fieldName)
	{
		CheckBox field = new SplitCheckBox(fieldLabel);
		field.setWidth("100%");
		field.setImmediate(true);

		if (group != null)
			group.bind(field, fieldName);
		form.addComponent(field);
		return field;

	}

	public ComboBox bindEntityField(String fieldLabel, String fieldName, String listFieldname, Class<E> listClazz)
	{
		ComboBox field = bindEntityField(form, group, fieldLabel, fieldName, listFieldname, listClazz);
		this.fieldList.add(field);
		return field;
	}

	static EntityManagerFactory entityManagerFactory;

	void setEntityManager(EntityManagerFactory factory)
	{
		entityManagerFactory = factory;
	}

	public <L> ComboBox bindEntityField(AbstractLayout form, ValidatingFieldGroup<E> fieldGroup, String fieldLabel,
			String fieldName, String listFieldName, Class<L> listClazz)
	{
		Preconditions
				.checkNotNull(entityManagerFactory, "You must provide the entity manager factory by calling setEntityManager first.");
		JPAContainer<?> container = JPAContainerFactory.make(listClazz, entityManagerFactory.getEntityManager());

		ComboBox field = new SplitComboBox(fieldLabel);

		field.setItemCaptionMode(ItemCaptionMode.PROPERTY);
		Preconditions.checkState(container.getContainerPropertyIds().contains(listFieldName),
				"valid listFieldNames are " + container.getContainerPropertyIds().toString());
		field.setItemCaptionPropertyId(listFieldName);
		field.setContainerDataSource(container);
		SingleSelectConverter<L> converter = new SingleSelectConverter<L>(field);
		field.setConverter(converter);
		field.setNewItemsAllowed(false);
		field.setNullSelectionAllowed(false);
		field.setTextInputAllowed(false);
		field.setWidth("100%");
		field.setImmediate(true);
		if (fieldGroup != null)
		{

			Preconditions.checkState(fieldGroup.getContainer().getContainerPropertyIds().contains(listFieldName),
					"valid listFieldNames are " + fieldGroup.getContainer().getContainerPropertyIds().toString());

			fieldGroup.bind(field, fieldName);
		}
		form.addComponent(field);
		return field;
	}

	static Container createContainerFromEnumClass(String fieldName, Class<?> clazz)
	{
		LinkedHashMap<Enum<?>, String> enumMap = new LinkedHashMap<Enum<?>, String>();
		for (Object enumConstant : clazz.getEnumConstants())
		{
			enumMap.put((Enum<?>) enumConstant, enumConstant.toString());
		}

		return createContainerFromMap(fieldName, enumMap);
	}

	@SuppressWarnings("unchecked")
	static public Container createContainerFromMap(String fieldName, Map<?, String> hashMap)
	{
		IndexedContainer container = new IndexedContainer();
		container.addContainerProperty(fieldName, String.class, "");

		Iterator<?> iter = hashMap.keySet().iterator();
		while (iter.hasNext())
		{
			Object itemId = iter.next();
			container.addItem(itemId);
			container.getItem(itemId).getItemProperty(fieldName).setValue(hashMap.get(itemId));
		}

		return container;
	}

	public ArrayList<AbstractComponent> getFieldList()
	{
		return this.fieldList;
	}

	public static void showConstraintViolation(ConstraintViolationException e)
	{
		// build constraint error
		StringBuilder sb = new StringBuilder();
		for (ConstraintViolation<?> violation : e.getConstraintViolations())
		{
			sb.append("Error: " + violation.getPropertyPath() + " : " + violation.getMessage() + "\n");
		}
		Notification.show(sb.toString(), Type.ERROR_MESSAGE);
	}

}
