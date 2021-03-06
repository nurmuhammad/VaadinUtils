package au.com.vaadinutils.crud;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import javax.persistence.PersistenceException;
import javax.persistence.metamodel.SingularAttribute;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.exceptions.DatabaseException;
import org.eclipse.persistence.exceptions.DescriptorException;
import org.vaadin.dialogs.ConfirmDialog;

import au.com.vaadinutils.crud.events.CrudEventDistributer;
import au.com.vaadinutils.crud.events.CrudEventType;
import au.com.vaadinutils.crud.security.SecurityManagerFactoryProxy;
import au.com.vaadinutils.dao.EntityManagerProvider;
import au.com.vaadinutils.listener.ClickEventLogged;
import au.com.vaadinutils.menu.Menu;
import au.com.vaadinutils.menu.Menus;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.vaadin.addon.jpacontainer.EntityItem;
import com.vaadin.addon.jpacontainer.EntityItemProperty;
import com.vaadin.addon.jpacontainer.JPAContainer;
import com.vaadin.addon.jpacontainer.JPAContainer.ProviderChangedEvent;
import com.vaadin.data.Buffered;
import com.vaadin.data.Buffered.SourceException;
import com.vaadin.data.Container.Filter;
import com.vaadin.data.Container.ItemSetChangeEvent;
import com.vaadin.data.Container.ItemSetChangeListener;
import com.vaadin.data.Validator.InvalidValueException;
import com.vaadin.data.fieldgroup.FieldGroup.CommitException;
import com.vaadin.event.FieldEvents.TextChangeEvent;
import com.vaadin.event.FieldEvents.TextChangeListener;
import com.vaadin.event.dd.DragAndDropEvent;
import com.vaadin.event.dd.DropHandler;
import com.vaadin.event.dd.acceptcriteria.AcceptCriterion;
import com.vaadin.event.dd.acceptcriteria.SourceIsTarget;
import com.vaadin.shared.ui.MarginInfo;
import com.vaadin.shared.ui.dd.VerticalDropLocation;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.AbstractLayout;
import com.vaadin.ui.AbstractSelect.AbstractSelectTargetDetails;
import com.vaadin.ui.AbstractTextField.TextChangeEventMode;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.Field;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import com.vaadin.ui.Panel;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.Table;
import com.vaadin.ui.Table.ColumnGenerator;
import com.vaadin.ui.Table.TableDragMode;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

public abstract class BaseCrudView<E extends CrudEntity> extends VerticalLayout implements RowChangeListener<E>,
		Selected<E>, DirtyListener, ButtonListener
{

	private static transient Logger logger = LogManager.getLogger(BaseCrudView.class);
	private static final long serialVersionUID = 1L;

	protected EntityItem<E> newEntity = null;
	/**
	 * When we enter inNew mode we need to hide the delete button. When we exit
	 * inNew mode thsi var is used to determine if we need to restore the delete
	 * button. i.e. if it wasn't visible before 'new' we shouldn't make it
	 * visible now.
	 */
	protected boolean restoreDelete;

	protected TextField searchField = new TextField();
	protected Button newButton = new Button("New");
	protected Button applyButton = new Button("Apply");
	protected Class<E> entityClass;

	protected ValidatingFieldGroup<E> fieldGroup;

	private VerticalLayout mainEditPanel = new VerticalLayout();

	// private E currentEntity;

	/*
	 * Any component can be bound to an external data source. This example uses
	 * just a dummy in-memory list, but there are many more practical
	 * implementations.
	 */
	protected JPAContainer<E> container;

	/* User interface components are stored in session. */
	protected EntityList<E> entityTable;
	protected VerticalLayout rightLayout;
	private Component editor;
	protected CrudPanelPair splitPanel;
	protected BaseCrudSaveCancelButtonTray buttonLayout;
	protected AbstractLayout advancedSearchLayout;
	private VerticalLayout searchLayout;
	protected Set<ChildCrudListener<E>> childCrudListeners = new HashSet<ChildCrudListener<E>>();
	private CrudDisplayMode displayMode = CrudDisplayMode.HORIZONTAL;
	protected HorizontalLayout actionLayout;
	protected ComboBox actionCombo;
	private boolean disallowEditing = false;
	private boolean disallowNew = false;
	private Label actionLabel;
	private boolean noEditor;
	public boolean advancedSearchOn = false;
	private Button advancedSearchCheckbox;
	private Set<RowChangedListener<E>> rowChangedListeners = new CopyOnWriteArraySet<RowChangedListener<E>>();
	private int minSearchTextLength = 0;
	private HeadingPropertySet<E> headings;

	protected BaseCrudView()
	{

	}

	BaseCrudView(CrudDisplayMode mode)
	{
		this.displayMode = mode;
	}

	protected void init(Class<E> entityClass, JPAContainer<E> container, HeadingPropertySet<E> headings)
	{
		this.entityClass = entityClass;
		this.container = container;
		try
		{
			container.setBuffered(true);
			container.setFireContainerItemSetChangeEvents(true);
			// container.setAutoCommit(true);

		}
		catch (Exception e)
		{
			logger.error(" ******* when constructing a jpaContainer for use with the BaseCrudView use JPAContainerFactory.makeBatchable ****** ");
			logger.error(e, e);
			throw new RuntimeException(e);
		}
		fieldGroup = new ValidatingFieldGroup<E>(container, entityClass);
		fieldGroup.setBuffered(true);

		// disable this, as the disabling of the save/cancel button is buggy
		// fieldGroup.setDirtyListener(this);

		this.headings = headings;
		entityTable = getTable(container, headings);
		entityTable.setRowChangeListener(this);
		entityTable.setSortEnabled(true);
		entityTable.setColumnCollapsingAllowed(true);

		// calling resetFilters here so the filters are in place when the page
		// first loads
		resetFilters();

		initLayout();

		initializeEntityTable();

		initSearch();
		initButtons();
		this.setVisible(true);
		showNoSelectionMessage();
		entityTable.select(entityTable.firstItemId());

		// do the security check after all the other setup, so extending classes
		// don't throw npe's due to
		// uninitialised components
		if (!getSecurityManager().canUserView())
		{
			this.setSizeFull();
			Label sorryMessage = new Label("Sorry, you do not have permission to access " + getTitleText());
			sorryMessage.setStyleName(ValoTheme.LABEL_H1);
			this.removeAllComponents();
			this.addComponent(sorryMessage);
			return;
		}

		if (!getSecurityManager().canUserDelete())
		{
			// disable delete as the user doesn't have permission to delete
			disallowDelete(true);
		}

		if (!getSecurityManager().canUserCreate())
		{
			disallowNew(true);
		}
		buttonLayout = new BaseCrudSaveCancelButtonTray(!getSecurityManager().canUserEdit() || disallowEditing,
				!getSecurityManager().canUserCreate() || disallowNew, this);

		rightLayout.addComponent(buttonLayout);
		// resetFilters();

	}

	protected void initializeEntityTable()
	{
		try
		{
			entityTable.init(this.getClass().getSimpleName());
		}
		catch (Exception e)
		{
			handleConstraintViolationException(e);

		}
	}

	/**
	 * allows the user to sort the items in the list via drag and drop
	 * 
	 * @param ordinalField
	 */
	public void enableDragAndDropOrdering(final SingularAttribute<E, Long> ordinalField)
	{
		container.sort(new Object[]
		{ ordinalField.getName() }, new boolean[]
		{ true });

		this.entityTable.setDragMode(TableDragMode.ROW);
		this.entityTable.setDropHandler(new DropHandler()
		{
			private static final long serialVersionUID = -6024948983201516170L;

			public AcceptCriterion getAcceptCriterion()
			{
				return SourceIsTarget.get();
			}

			@SuppressWarnings("unchecked")
			@Override
			public void drop(DragAndDropEvent event)
			{

				if (isDirty())
				{
					Notification.show("You must save first", Type.WARNING_MESSAGE);
					return;
				}
				Object draggedItemId = event.getTransferable().getData("itemId");

				AbstractSelectTargetDetails td = (AbstractSelectTargetDetails) event.getTargetDetails();
				VerticalDropLocation dl = td.getDropLocation();

				Object targetId = ((AbstractSelectTargetDetails) event.getTargetDetails()).getItemIdOver();
				int idx = container.indexOfId(targetId);
				if (dl == VerticalDropLocation.BOTTOM)
				{
					idx++;
				}
				else if (dl == VerticalDropLocation.TOP)
				{
					// no need to change the idx here
				}
				targetId = container.getIdByIndex(idx);
				if (targetId == null)
				{
					Notification.show("Cant drag below the last row, try moving the last row up.", Type.ERROR_MESSAGE);
					return;
				}

				EntityItem<E> dragged = container.getItem(draggedItemId);
				EntityItem<E> target = container.getItem(targetId);

				if (target != null)
				{
					// EntityItemProperty targetOrdinalProp =
					// target.getItemProperty(ordinalField.getName());
					// Long targetOrdinal = (Long) targetOrdinalProp.getValue();
				}
				EntityItemProperty draggedOrdinalProp = dragged.getItemProperty(ordinalField.getName());
				// Long draggedOrdinal = (Long) draggedOrdinalProp.getValue();

				boolean added = false;
				Long ctr = 1l;

				for (Object id : container.getItemIds())
				{
					if (id.equals(targetId))
					{
						draggedOrdinalProp.setValue(ctr++);
						added = true;

					}
					if (!id.equals(draggedItemId))
					{
						container.getItem(id).getItemProperty(ordinalField.getName()).setValue(ctr++);
					}
				}
				if (!added)
				{
					draggedOrdinalProp.setValue(ctr++);

				}

				container.commit();
				container.refresh();
				container.sort(new Object[]
				{ ordinalField.getName() }, new boolean[]
				{ true });

				// cause this crud to save, or if its a child cause the parent
				// to save.
				try
				{
					invokeTopLevelCrudSave();
				}
				catch (Exception e)
				{
					Notification.show(e.getMessage(), Type.ERROR_MESSAGE);
					handleConstraintViolationException(e);
				}

			}
		});

	}

	/**
	 * the child crud variant of this method calls parent.save();
	 */
	protected void invokeTopLevelCrudSave()
	{
		save();
	}

	/**
	 * if you need to provide a security manager, call
	 * SecurityManagerFactoryProxy.setFactory(...) at application initialisation
	 * time
	 * 
	 * @return
	 * @throws ExecutionException
	 */
	public CrudSecurityManager getSecurityManager()
	{
		return SecurityManagerFactoryProxy.getSecurityManager(this.getClass());
	}

	public void addGeneratedColumn(final Object id, ColumnGenerator generator)
	{
		Preconditions.checkState(entityTable != null, "call BaseCrudView.init() first");
		Object idName = id;
		if (id instanceof SingularAttribute)
		{
			idName = ((SingularAttribute<?, ?>) id).getName();
		}
		entityTable.addGeneratedColumn(idName, generator);
	}

	protected EntityList<E> getTable(JPAContainer<E> container, HeadingPropertySet<E> headings)
	{
		return new EntityTable<E>(container, headings);
	}

	/*
	 * build the button layout and editor panel
	 */

	protected abstract Component buildEditor(ValidatingFieldGroup<E> fieldGroup2);

	private void initLayout()
	{
		this.setSizeFull();

		splitPanel = displayMode.getContainer();
		this.addComponent(splitPanel.getPanel());
		this.setExpandRatio(splitPanel.getPanel(), 1);
		this.setSizeFull();

		// Layout for the tablesaveOnRowChange
		VerticalLayout leftLayout = new VerticalLayout();

		// Start by defining the LHS which contains the table
		splitPanel.setFirstComponent(leftLayout);
		searchLayout = new VerticalLayout();
		searchLayout.setWidth("100%");
		searchField.setWidth("100%");

		// expandratio and use of setSizeFull are incompatible
		// searchLayout.setSizeFull();

		Component title = getTitle();
		if (title != null)
		{
			leftLayout.addComponent(getTitle());
		}

		leftLayout.addComponent(searchLayout);

		buildSearchBar();

		leftLayout.addComponent(entityTable);
		leftLayout.setSizeFull();

		/*
		 * On the left side, expand the size of the entity List so that it uses
		 * all the space left after from bottomLeftLayout
		 */
		leftLayout.setExpandRatio(entityTable, 1);
		entityTable.setSizeFull();

		// Now define the edit area
		rightLayout = new VerticalLayout();
		splitPanel.setSecondComponent(rightLayout);

		/* Put a little margin around the fields in the right side editor */
		Panel scroll = new Panel();
		// mainEditPanel.setDescription("BaseCrud:MainEditPanel");

		if (!noEditor)
		{
			mainEditPanel.setVisible(true);
			mainEditPanel.setSizeFull();
			mainEditPanel.setId("MailEditPanel");
			scroll.setSizeFull();
			scroll.setContent(mainEditPanel);
			rightLayout.addComponent(scroll);
			rightLayout.setExpandRatio(scroll, 1.0f);
			rightLayout.setSizeFull();
			rightLayout.setId("rightLayout");

			editor = buildEditor(fieldGroup);
			Preconditions.checkNotNull(
					editor,
					"Your editor implementation returned null!, you better create an editor. "
							+ entityClass.getSimpleName());
			mainEditPanel.addComponent(editor);

		}
		else
		{
			this.setSplitPosition(100);
			splitPanel.setLocked();
		}
		buildActionLayout();

		leftLayout.addComponent(actionLayout);

		rightLayout.setVisible(false);
	}

	/**
	 * call this method before init if you intend not to provide an editor
	 */
	public void noEditor()
	{
		noEditor = true;

	}

	/**
	 * get the title for the page from the menu annotation, override this menu
	 * to provide a custom page title
	 * 
	 * @return
	 */
	protected String getTitleText()
	{

		Annotation annotation = this.getClass().getAnnotation(Menu.class);
		if (annotation instanceof Menu)
		{
			return ((Menu) annotation).display();
		}
		annotation = this.getClass().getAnnotation(Menus.class);
		if (annotation instanceof Menus)
		{
			return ((Menus) annotation).menus()[0].display();
		}

		return "Override getTitleText() to set a custom title. " + this.getClass().getCanonicalName();
	}

	protected Component getTitle()
	{
		HorizontalLayout holder = new HorizontalLayout();

		Label titleLabel = new Label(getTitleText());

		titleLabel.addStyleName(ValoTheme.LABEL_H2);
		titleLabel.addStyleName(ValoTheme.LABEL_BOLD);

		holder.addComponent(titleLabel);
		holder.setComponentAlignment(titleLabel, Alignment.MIDDLE_RIGHT);
		return holder;
	}

	private void buildActionLayout()
	{
		actionLayout = new HorizontalLayout();
		actionLayout.setWidth("100%");
		actionLayout.setMargin(new MarginInfo(false, true, false, false));

		actionLabel = new Label("&nbsp;Action");
		actionLabel.setContentMode(ContentMode.HTML);
		actionLabel.setWidth("50");
		CssLayout group = new CssLayout();
		group.addStyleName("v-component-group");
		actionLayout.addComponent(group);
		group.addComponent(actionLabel);

		actionCombo = new ComboBox(null);
		actionCombo.setWidth("160");
		actionCombo.setNullSelectionAllowed(false);
		actionCombo.setTextInputAllowed(false);

		group.addComponent(actionCombo);

		addCrudActions();
		group.addComponent(applyButton);

		newButton.setCaption(getNewButtonLabel());
		newButton.setId("CrudNewButton");
		actionLayout.addComponent(newButton);

		// tweak the alignments.
		actionLayout.setComponentAlignment(group, Alignment.MIDDLE_LEFT);
		actionLayout.setComponentAlignment(newButton, Alignment.MIDDLE_RIGHT);
		actionLayout.setExpandRatio(group, 1.0f);

		actionLayout.setHeight("35");
	}

	protected String getNewButtonLabel()
	{
		return "New";
	}

	private void addCrudActions()
	{
		/**
		 * Add the set of actions in.
		 */
		CrudAction<E> defaultAction = null;
		for (CrudAction<E> action : getCrudActions())
		{
			if (action.isDefault())
			{
				Preconditions.checkState(defaultAction == null, "Only one action may be marked as default: "
						+ (defaultAction != null ? defaultAction.toString() : "") + " was already the default when "
						+ action.toString() + " was found to also be default.");
				defaultAction = action;
			}
			actionCombo.addItem(action);

		}

		// Select the default action
		actionCombo.setValue(defaultAction);
	}

	/**
	 * overload this method to add custom actions, in your overloaded version
	 * you should call super.getCrudActions() to get a list with the
	 * DeleteAction pre-populated
	 * 
	 * if you need a specialized form of delete that does things like delete
	 * files, there is a callback on the CrudActionDelete class for that
	 */
	protected List<CrudAction<E>> getCrudActions()
	{
		List<CrudAction<E>> actions = new LinkedList<CrudAction<E>>();
		CrudAction<E> crudAction = new CrudActionDelete<E>();
		actions.add(crudAction);

		CrudAction<E> exportAction = new CrudAction<E>()
		{

			private static final long serialVersionUID = -7703959823800614876L;

			@Override
			public boolean isDefault()
			{
				return false;
			}

			@Override
			public void exec(BaseCrudView<E> crud, EntityItem<E> entity)
			{

				new ContainerCSVExport(getTitleText(), (Table) entityTable, headings);

			}

			@Override
			public String toString()
			{
				return "Export CSV Data";
			}
		};
		actions.add(exportAction);

		return actions;
	}

	private void buildSearchBar()
	{
		AbstractLayout group = new HorizontalLayout();
		if (UI.getCurrent().getTheme() == ValoTheme.class.getSimpleName())
		{
			group = new CssLayout();
			group.addStyleName("v-component-group");
		}

		group.setSizeFull();

		searchLayout.addComponent(group);

		AbstractLayout advancedSearch = buildAdvancedSearch();
		if (advancedSearch != null)
		{
			group.addComponent(advancedSearchCheckbox);
		}

		Button clear = createClearButton();

		group.addComponent(clear);

		// searchField.setWidth("80%");
		searchField.setId("CrudSearchField");
		group.addComponent(searchField);
		if (group instanceof HorizontalLayout)
		{
			((HorizontalLayout) group).setExpandRatio(searchField, 1);
		}

	}

	private Button createClearButton()
	{

		Button clear = new Button("X");
		clear.setImmediate(true);
		clear.addClickListener(new ClickEventLogged.ClickListener()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void clicked(ClickEvent event)
			{
				searchField.setValue("");
				clearAdvancedFilters();
				triggerFilter();

			}

		});
		return clear;
	}

	private AbstractLayout buildAdvancedSearch()
	{
		advancedSearchLayout = getAdvancedSearchLayout();
		if (advancedSearchLayout != null)
		{

			advancedSearchCheckbox = new Button("Advanced");
			advancedSearchOn = false;

			advancedSearchCheckbox.setImmediate(true);
			advancedSearchCheckbox.addClickListener(new ClickListener()
			{

				private static final long serialVersionUID = 7777043506655571664L;

				@Override
				public void buttonClick(ClickEvent event)
				{
					clearAdvancedFilters();
					advancedSearchOn = !advancedSearchOn;
					advancedSearchLayout.setVisible(advancedSearchOn);
					if (!advancedSearchOn)
					{
						triggerFilter();
					}
					if (!advancedSearchOn)
					{
						advancedSearchCheckbox.removeStyleName(ValoTheme.BUTTON_FRIENDLY);
					}
					else
					{
						advancedSearchCheckbox.setStyleName(ValoTheme.BUTTON_FRIENDLY);
					}

				}
			});

			searchLayout.addComponent(advancedSearchLayout);
			advancedSearchLayout.setVisible(false);
		}
		return advancedSearchLayout;
	}

	public void showAdvancedSearch()
	{
		advancedSearchOn = true;
		advancedSearchLayout.setVisible(advancedSearchOn);
		advancedSearchCheckbox.setStyleName(ValoTheme.BUTTON_FRIENDLY);

	}

	protected AbstractLayout getAdvancedSearchLayout()
	{
		return null;
	}

	/**
	 * Used when creating a 'new' record to disable actions such as 'new' and
	 * delete until the record is saved.
	 * 
	 * @param show
	 */
	protected void activateEditMode(boolean activate)
	{
		actionCombo.setEnabled(!activate);
		applyButton.setEnabled(!activate);

		boolean showNew = !activate;
		if (disallowNew)
			showNew = false;
		newButton.setEnabled(showNew);
	}

	/**
	 * A child class can call this method to stop a user from being able to edit
	 * a record. When called the Save/Cancel buttons are disabled from the
	 * screen.
	 * 
	 * If you also set hideSaveCancelLayout to true then the save/cancel buttons
	 * will be completely removed from the layout.
	 * 
	 * By default editing is allowed.
	 * 
	 * @param disallow
	 */
	protected void disallowEdit(boolean disallow)
	{
		Preconditions.checkArgument(buttonLayout == null, "You must call disallowEdit before init");
		this.disallowEditing = disallow;
	}

	/**
	 * A child class can call this method to stop a user from being able to add
	 * new records.
	 * 
	 * When called the 'New' button is removed from the UI.
	 * 
	 * By default adding new records is allowed.
	 * 
	 * @param disallow
	 */
	protected void disallowNew(boolean disallow)
	{
		Preconditions.checkArgument(buttonLayout == null, "You must call disallowEdit before init");

		this.disallowNew = disallow;
		showNew(!disallow);
	}

	protected boolean isDisallowNew()
	{
		return this.disallowNew;
	}

	/**
	 * A child class can call this method to stop a user from being able to
	 * delete a record. When called the delete action is removed from the action
	 * combo. If the delete is the only action then the action combo and apply
	 * button will also be removed.
	 * 
	 * By default deleting is allowed.
	 * 
	 * @param disallow
	 */
	protected void disallowDelete(boolean disallow)
	{

		if (disallow || !getSecurityManager().canUserDelete())
		{
			// find and remove the delete action
			for (Object id : this.actionCombo.getItemIds())
			{
				if (id instanceof CrudActionDelete)
				{
					this.actionCombo.removeItem(id);
					break;
				}
			}
			if (this.actionCombo.size() == 0)
			{
				this.actionCombo.setVisible(false);
				this.applyButton.setVisible(false);
				this.actionLabel.setVisible(false);
			}
		}
		else
		{
			this.actionCombo.removeAllItems();
			addCrudActions();
			this.actionCombo.setVisible(true);
			this.applyButton.setVisible(true);
			this.actionLabel.setVisible(true);
		}

	}

	/**
	 * Internal method to show hide the new button when editing.
	 * 
	 * If the user has called disallowNew then the new button will never be
	 * displayed.
	 */
	private void showNew(boolean show)
	{
		if (disallowNew)
			show = false;

		newButton.setVisible(show);
	}

	/**
	 * Hides the Action layout which contains the 'New' button and 'Action'
	 * combo.
	 * 
	 * Hiding the action layout effectively stops the user from creating new
	 * records or applying any action such as deleting a record.
	 * 
	 * Hiding the action layout provides more room for the list of records.
	 * 
	 * @param show
	 */
	protected void showActionLayout(boolean show)
	{
		this.actionLayout.setVisible(show);
	}

	public void setSplitPosition(float pos)
	{
		splitPanel.setSplitPosition(pos);
	}

	private void initButtons()
	{
		newButton.addClickListener(new ClickEventLogged.ClickListener()
		{
			private static final long serialVersionUID = 1L;

			public void clicked(ClickEvent event)
			{

				newClicked();

			}

		});

		applyButton.addClickListener(new ClickEventLogged.ClickListener()
		{
			private static final long serialVersionUID = 1L;

			@Override
			public void clicked(ClickEvent event)
			{
				Object entityId = entityTable.getValue();
				if (entityId != null)
				{
					EntityItem<E> entity = container.getItem(entityId);

					@SuppressWarnings("unchecked")
					CrudAction<E> action = (CrudAction<E>) actionCombo.getValue();
					if (action != null)
					{
						if (interceptAction(action, entity))
							action.exec(BaseCrudView.this, entity);
						container.commit();
						container.refreshItem(entity.getItemId());
						// actionCombo.select(actionCombo.getNullSelectionItemId());
					}
					else
					{
						Notification.show("Please select an Action first.");
					}
				}
				else
					Notification.show("Please select record first.");
			}
		});

	}

	public void cancelClicked()
	{
		fieldGroup.discard();
		for (ChildCrudListener<E> child : childCrudListeners)
		{
			child.discard();
		}

		if (newEntity != null)
		{
			if (restoreDelete)
			{
				activateEditMode(false);
				restoreDelete = false;
			}
			newEntity = null;

			// set the selection to the first item on the page.
			// We need to set it to null first as if the first item was
			// already selected
			// then we won't get a row change which is need to update
			// the rhs.
			// CONSIDER: On the other hand I'm concerned that we might
			// confuse people as they
			// get two row changes events.
			BaseCrudView.this.entityTable.select(null);
			BaseCrudView.this.entityTable.select(entityTable.getCurrentPageFirstItemId());

		}
		else
		{
			// Force the row to be reselected so that derived
			// classes get a rowChange when we cancel.
			// CONSIDER: is there a better way of doing this?
			// Could we not just fire an 'onCancel' event or similar?
			Long id = null;
			if (entityTable.getCurrent() != null)
			{
				id = entityTable.getCurrent().getEntity().getId();
			}
			BaseCrudView.this.entityTable.select(null);
			BaseCrudView.this.entityTable.select(id);

		}
		splitPanel.showFirstComponet();
		if (entityTable.getCurrent() == null)
		{
			showNoSelectionMessage();
		}

		Notification.show("Changes discarded.", "Any changes you have made to this record been discarded.",
				Type.TRAY_NOTIFICATION);
		buttonLayout.setDefaultState();
	}

	/**
	 * Override this method to intercept activation of an action.
	 * 
	 * Return true if you are happy for the action to proceed otherwise return
	 * false if you want to suppress the action.
	 * 
	 * When suppressing the action you should display a notification as to why
	 * you suppressed it.
	 * 
	 * @param action
	 * @param entity
	 * @return
	 */
	protected boolean interceptAction(CrudAction<E> action, EntityItem<E> entity)
	{
		return true;
	}

	public void delete()
	{
		E deltedEntity = entityTable.getCurrent().getEntity();
		Object entityId = entityTable.getValue();
		Object previousItemId = entityTable.prevItemId(entityId);
		entityTable.removeItem(entityId);
		newEntity = null;

		preDelete(deltedEntity);
		// set the selection to the first item
		// on the page.
		// We need to set it to null first as if
		// the first item was already selected
		// then we won't get a row change which
		// is need to update the rhs.
		// CONSIDER: On the other hand I'm
		// concerned that we might confuse
		// developers as they
		// get two row changes events.

		BaseCrudView.this.entityTable.select(null);
		if (previousItemId != null)
		{
			BaseCrudView.this.entityTable.select(previousItemId);
		}
		else
		{
			entityTable.select(entityTable.firstItemId());
		}
		container.commit();

		EntityManagerProvider.getEntityManager().flush();

		postDelete(deltedEntity);

		CrudEventDistributer.publishEvent(this, CrudEventType.DELETE, deltedEntity);

	}

	/**
	 * hook for implementations that need to do some additional cleanup before a
	 * delete.
	 * 
	 */
	protected void preDelete(E entity)
	{

	}

	/**
	 * hook for implementations that need to do some additional cleanup after a
	 * delete.
	 * 
	 */
	protected void postDelete(E entity)
	{

	}

	public void save()
	{
		boolean selected = false;
		try
		{
			commitFieldGroup();
			CrudEventType eventType = CrudEventType.EDIT;
			if (newEntity != null)
			{
				if (!okToSave(newEntity))
				{
					return;
				}
				eventType = CrudEventType.CREATE;
				interceptSaveValues(newEntity);

				Object id = container.addEntity(newEntity.getEntity());
				EntityItem<E> item = container.getItem(id);

				fieldGroup.setItemDataSource(item);
				selected = true;

				if (restoreDelete)
				{
					activateEditMode(false);
					restoreDelete = false;
				}
			}
			else
			{
				EntityItem<E> current = entityTable.getCurrent();
				if (current != null)
				{
					if (!okToSave(current))
					{
						return;
					}
					interceptSaveValues(current);
				}
			}

			// commit the row to the database, and retrieve the possibly new
			// entity
			E newEntity = commitContainerAndGetEntityFromDB();
			if (newEntity == null)
			{
				throw new RuntimeException(
						"An error occurred, unable to retrieve updated record. Failed to save changes");
			}

			newEntity = EntityManagerProvider.getEntityManager().merge(newEntity);
			for (ChildCrudListener<E> commitListener : childCrudListeners)
			{
				// only commit dirty children, saves time for a crud with lots
				// of children
				if (commitListener.isDirty() || !(commitListener instanceof ChildCrudView))
				{
					commitListener.committed(newEntity);
				}
			}
			EntityManagerProvider.getEntityManager().flush();

			// children may have been added to the parent, evict the parent from
			// the JPA cache so it will get updated
			EntityManagerProvider.getEntityManager().getEntityManagerFactory().getCache()
					.evict(entityClass, newEntity.getId());

			newEntity = EntityManagerProvider.merge(newEntity);
			postSaveAction(newEntity);
			EntityManagerProvider.getEntityManager().flush();

			container.refreshItem(newEntity.getId());
			CrudEventDistributer.publishEvent(this, eventType, newEntity);

			if (eventType == CrudEventType.CREATE)
			{
				addFilterToShowNewRow(newEntity);
				container.discard();
			}
			this.newEntity = null;

			// select has been moved to here because when it happens earlier,
			// child cruds are caused to discard their data before saving it for
			// a new record

			entityTable.select(newEntity.getId());

			splitPanel.showFirstComponet();
			Notification.show("Changes Saved", "Any changes you have made have been saved.", Type.TRAY_NOTIFICATION);

			// return save/edit buttons to default settings
			buttonLayout.setDefaultState();

		}

		catch (Exception e)
		{
			if (e.getCause() instanceof PersistenceException)
			{
				handlePersistenceException(e);
			}
			else if (e instanceof InvalidValueException)
			{
				handleInvalidValueException((InvalidValueException) e);
			}
			else if (e.getCause() instanceof InvalidValueException)
			{
				handleInvalidValueException((InvalidValueException) e.getCause());
			}
			else
			{
				handleConstraintViolationException(e);
			}
		}
		finally
		{
			if (newEntity != null)
			{

				if (selected && entityTable.getCurrent() != null)
				{
					container.removeItem(entityTable.getCurrent());
				}
			}
			buttonLayout.setDefaultState();

		}

	}

	protected void addFilterToShowNewRow(E id)
	{
		// TODO Auto-generated method stub

	}

	protected void handleInvalidValueException(InvalidValueException m)
	{
		String causeMessage = "";
		for (InvalidValueException cause : m.getCauses())
		{
			causeMessage += cause.getMessage() + ". ";
		}
		if (m.getMessage() != null && m.getMessage().length() > 0)
		{
			causeMessage += m.getMessage() + ". ";
		}
		Notification.show("Please fix the form errors and then try again.\n\n " + causeMessage, Type.ERROR_MESSAGE);
	}

	/**
	 * @throws Exception
	 */
	protected boolean okToSave(EntityItem<E> entity) throws Exception
	{
		return true;
	}

	static private void handlePersistenceException(Exception e)
	{
		if (e.getCause() instanceof PersistenceException)
		{
			String tmp = e.getMessage();
			PersistenceException pex = (PersistenceException) e.getCause();
			if (pex.getCause() instanceof DatabaseException)
			{
				DatabaseException dex = (DatabaseException) pex.getCause();
				tmp = dex.getMessage();
				if (tmp.indexOf("Query being") > 0)
				{
					// strip of the query
					tmp = tmp.substring(0, tmp.indexOf("Query being"));

					if (tmp.contains("MySQL"))
					{
						tmp = tmp.substring(tmp.indexOf("MySQL") + 5);
					}
				}
			}
			logger.error(e, e);
			throw new RuntimeException(tmp);
		}
	}

	/**
	 * logs the initial error and calls the recusive version of it'self. always
	 * throws a runtime exception
	 * 
	 * @param e
	 */
	public static void handleConstraintViolationException(Throwable e)
	{
		if (e instanceof RuntimeException && e.getCause() instanceof Buffered.SourceException)
		{
			SourceException ex = (Buffered.SourceException) e.getCause();
			if (ex.getCause() instanceof PersistenceException)
			{
				handlePersistenceException(ex);
			}

		}
		logger.error(e, e);
		handleConstraintViolationException(e, 5);
		throw new RuntimeException(e);
	}

	/**
	 * digs down looking for a useful exception, it will throw a runtime
	 * exception if it finds an useful exception
	 * 
	 * @param e
	 * @param nestLimit
	 */
	private static void handleConstraintViolationException(Throwable e, int nestLimit)
	{
		if (nestLimit > 0 && e != null)
		{
			nestLimit--;
			if (e instanceof DescriptorException)
			{
				DescriptorException desc = (DescriptorException) e;
				Notification.show(desc.getMessage(), Type.ERROR_MESSAGE);

				throw new RuntimeException(desc.getMessage());
			}
			if (e instanceof ConstraintViolationException)
			{
				String groupedViolationMessage = e.getClass().getSimpleName() + " ";
				for (ConstraintViolation<?> violation : ((ConstraintViolationException) e).getConstraintViolations())
				{
					logger.error(violation.getLeafBean().getClass().getCanonicalName() + " " + violation.getLeafBean());
					String violationMessage = violation.getLeafBean().getClass().getSimpleName() + " "
							+ violation.getPropertyPath() + " " + violation.getMessage() + ", the value was "
							+ violation.getInvalidValue();
					logger.error(violationMessage);
					groupedViolationMessage += violationMessage + "\n";
				}
				Notification.show(groupedViolationMessage, Type.ERROR_MESSAGE);
				throw new RuntimeException(groupedViolationMessage);
			}

			handleConstraintViolationException(e.getCause(), nestLimit);

		}
	}

	/**
	 * commits the container and retrieves the new recordid
	 * 
	 * we have to hook the ItemSetChangeListener to be able to get the database
	 * id of a new record.
	 */
	private E commitContainerAndGetEntityFromDB()
	{
		// don't really need an AtomicReference, just using it as a mutable
		// final variable to be used in the callback
		final AtomicReference<E> newEntity = new AtomicReference<E>();

		// call back to collect the id of the new record when the container
		// fires the ItemSetChangeEvent
		ItemSetChangeListener tmp = new ItemSetChangeListener()
		{

			/**
			 * 
			 */
			private static final long serialVersionUID = 9132090066374531277L;

			@Override
			public void containerItemSetChange(ItemSetChangeEvent event)
			{
				if (event instanceof ProviderChangedEvent)
				{
					@SuppressWarnings("rawtypes")
					ProviderChangedEvent pce = (ProviderChangedEvent) event;
					@SuppressWarnings("unchecked")
					Collection<E> affectedEntities = pce.getChangeEvent().getAffectedEntities();

					if (affectedEntities.size() > 0)
					{
						@SuppressWarnings("unchecked")
						E id = (E) affectedEntities.toArray()[0];
						newEntity.set(id);

					}
				}
			}
		};

		try
		{
			// add the listener
			container.addItemSetChangeListener(tmp);
			// call commit
			container.commit();
			newEntity.set(EntityManagerProvider.getEntityManager().merge(newEntity.get()));
		}
		catch (Exception e)
		{
			handleConstraintViolationException(e);
		}
		finally
		{
			// detach the listener
			container.removeItemSetChangeListener(tmp);
		}

		// return the entity
		return newEntity.get();
	}

	/**
	 * called after a record has been committed to the database
	 */
	protected void postSaveAction(E entityItem)
	{

	}

	/**
	 * opportunity for implementing classes to modify or add data to the entity
	 * being saved.
	 * 
	 * NOTE: modify the item properties not the entity as accessing the entity
	 * is unreliable
	 * 
	 * @param item
	 * @throws Exception
	 */
	protected void interceptSaveValues(EntityItem<E> entityItem) throws Exception
	{
	}

	private void initSearch()
	{
		searchField.setInputPrompt("Search");
		searchField.setTextChangeEventMode(TextChangeEventMode.LAZY);
		searchField.setImmediate(true);
		searchField.addTextChangeListener(new TextChangeListener()
		{
			private static final long serialVersionUID = 1L;

			public void textChange(final TextChangeEvent event)
			{
				try
				{
					// If advanced search is active then it should be
					// responsible
					// for triggering the filter.
					if (!advancedSearchOn)
					{
						String filterString = event.getText();
						if (filterString.length() >= minSearchTextLength)
						{
							triggerFilter(filterString);
						}
					}
				}
				catch (Exception e)
				{
					logger.error(e, e);
					Notification.show(e.getClass().getSimpleName() + " " + e.getMessage(), Type.ERROR_MESSAGE);
				}
			}

		});

		searchField.focus();
	}

	/**
	 * the minimum number of characters entered in to the search field required
	 * to trigger a search, default is 0
	 * 
	 * @param minLength
	 */
	public void setMinimumSearchTextLength(int minLength)
	{
		minSearchTextLength = minLength;
	}

	/**
	 * call this method to cause filters to be applied
	 */
	protected void triggerFilter()
	{
		triggerFilter(searchField.getValue().trim());
	}

	int emptyFilterWarningCount = 3;

	protected void triggerFilter(String searchText)
	{
		boolean advancedSearchActive = advancedSearchOn;

		Filter filter = getContainerFilter(searchText, advancedSearchActive);
		if (filter == null && emptyFilterWarningCount-- > 0)
		{
			logger.warn("({}.java:1) getContainerFilter() returned NULL", this.getClass().getCanonicalName());

		}

		applyFilter(filter);

	}

	public void applyFilter(final Filter filter)
	{
		try
		{
			/* Reset the filter for the Entity Container. */
			resetFilters();
			if (filter != null)
			{
				container.addContainerFilter(filter);
			}
			container.discard();

			entityTable.select(entityTable.firstItemId());
		}
		catch (Exception e)
		{
			handleConstraintViolationException(e);
		}

	}

	protected String getSearchFieldText()
	{
		return searchField.getValue();
	}

	/**
	 * create a filter for the text supplied, the text is as entered in the text
	 * search bar.
	 * 
	 * @param string
	 * @return
	 */
	abstract protected Filter getContainerFilter(String filterString, boolean advancedSearchActive);

	/**
	 * called when the advancedFilters layout should clear it's values
	 */
	protected void clearAdvancedFilters()
	{

	}

	@Override
	/** Called when the currently selected row in the 
	 *  table part of this view has changed.
	 *  We use this to update the editor's current item.
	 */
	public void allowRowChange(final RowChangeCallback callback)
	{

		boolean dirty = false;
		for (ChildCrudListener<E> commitListener : childCrudListeners)
		{
			dirty |= commitListener.isDirty();
		}

		if (fieldGroup.isModified() || newEntity != null || dirty)
		{
			ConfirmDialog
					.show(UI.getCurrent(),
							"Discard changes?",
							"You have unsaved changes for this record. Continuing will result in those changes being discarded. ",
							"Continue", "Cancel", new ConfirmDialog.Listener()
							{
								private static final long serialVersionUID = 1L;

								public void onClose(ConfirmDialog dialog)
								{
									if (dialog.isConfirmed())
									{
										/*
										 * When an entity is selected from the
										 * list, we want to show that in our
										 * editor on the right. This is nicely
										 * done by the FieldGroup that binds all
										 * the fields to the corresponding
										 * Properties in our entity at once.
										 */
										fieldGroup.discard();

										for (ChildCrudListener<E> child : childCrudListeners)
										{
											child.discard();
										}

										if (restoreDelete)
										{
											activateEditMode(false);
											restoreDelete = false;
										}

										newEntity = null;

										callback.allowRowChange();

									}
									else
									{
										// User did not confirm so don't allow
										// the change.

									}
								}
							});
		}
		else
		{
			try
			{
				callback.allowRowChange();

			}
			catch (Exception e)
			{
				handleConstraintViolationException(e);

			}
		}

	}

	@Override
	/** Called when the currently selected row in the 
	 *  table part of this view has changed.
	 *  We use this to update the editor's current item.
	 *  
	 *  @item the item that is now selected. This may be null if selection has been lost.
	 */
	public void rowChanged(EntityItem<E> item)
	{

		splitPanel.showSecondComponet();
		fieldGroup.setItemDataSource(item);

		Map<String, Long> times = new HashMap<>();
		// notifiy ChildCrudView's that we've changed row.
		for (ChildCrudListener<E> commitListener : childCrudListeners)
		{
			Stopwatch timer = new Stopwatch();
			timer.start();
			commitListener.selectedParentRowChanged(item);
			times.put(commitListener.getClass().getSimpleName() + ":" + commitListener.hashCode(),
					timer.elapsedMillis());
		}

		if (item != null || newEntity != null)
		{
			splitPanel.setSecondComponent(rightLayout);
		}
		else
		{
			showNoSelectionMessage();
		}

		rightLayout.setVisible(item != null || newEntity != null);
		if (item == null)
		{
			notifyRowChangedListeners(null);
		}
		else
		{
			notifyRowChangedListeners(item.getEntity());
		}
		for (Entry<String, Long> time : times.entrySet())
		{
			logger.info("{}: {}ms", time.getKey(), time.getValue());
		}

	}

	protected void showNoSelectionMessage()
	{
		String message = "";
		if (newButton.isVisible())
		{
			message = "Click New to create a new record.";
			if (entityTable.firstItemId() != null)
			{
				message = "Click New to create a new record or click an existing "
						+ "record to view and or edit the records details.";
			}

		}
		else
		{
			if (entityTable.firstItemId() != null)
			{
				message = "click an existing record to view and or edit the records details.";
			}
			else
			{
				message = "No records were found.";
			}

		}
		VerticalLayout pane = new VerticalLayout();
		pane.setSizeFull();
		Label label = new Label(message);
		label.setWidth("300");
		label.setContentMode(ContentMode.HTML);

		pane.addComponent(label);
		pane.setComponentAlignment(label, Alignment.MIDDLE_CENTER);
		splitPanel.setSecondComponent(pane);
	}

	protected void commitFieldGroup() throws CommitException
	{
		formValidate();
		String fieldName = selectFirstErrorFieldAndShowTab();
		if (!fieldGroup.isValid())
		{

			throw new InvalidValueException("Invalid Field: " + fieldName);

		}
		fieldGroup.commit();
		for (ChildCrudListener<E> child : childCrudListeners)
		{
			child.validateFieldz();
		}

	}

	/**
	 * Overload this method to provide cross-field (form level) validation.
	 * 
	 * @return
	 */
	protected void formValidate() throws InvalidValueException
	{
	}

	VerticalLayout getEmptyPanel()
	{
		VerticalLayout layout = new VerticalLayout();

		Label pleaseAdd = new Label(
				"Click the 'New' button to add a new Record or click an existing record in the adjacent table to edit it.");
		layout.addComponent(pleaseAdd);
		layout.setComponentAlignment(pleaseAdd, Alignment.MIDDLE_CENTER);
		layout.setSizeFull();
		return layout;
	}

	@Override
	public E getCurrent()
	{
		E entity = null;
		if (newEntity != null)
			entity = newEntity.getEntity();
		if (entity == null)
		{
			EntityItem<E> entityItem = entityTable.getCurrent();
			if (entityItem != null)
			{
				entity = entityItem.getEntity();
			}
		}
		return entity;
	}

	/**
	 * update the container and editor with any changes from the db.
	 */
	public void updateEditorFromDb()
	{
		Preconditions.checkState(!isDirty(), "The editor is dirty, save or cancel first.");

		E entity = entityTable.getCurrent().getEntity();
		container.refresh();
		entityTable.select(null);
		entityTable.select(entity.getId());

	}

	/**
	 * check if the editor has changes
	 * 
	 * @return
	 */
	public boolean isDirty()
	{
		return fieldGroup.isModified() || newEntity != null;
	}

	/**
	 * a ChildCrudView adds it's self here so it will be notified when the
	 * parent saves
	 * 
	 * @param listener
	 */
	public void addChildCrudListener(ChildCrudListener<E> listener)
	{
		childCrudListeners.add(listener);
	}

	public void newClicked()
	{
		/*
		 * Rows in the Container data model are called Item. Here we add a new
		 * row in the beginning of the list.
		 */

		allowRowChange(new RowChangeCallback()
		{

			@Override
			public void allowRowChange()
			{
				try
				{
					E previousEntity = getCurrent();
//					searchField.setValue("");
//					resetFilters();

					createNewEntity(previousEntity);

					rowChanged(newEntity);
					// Can't delete when you are adding a new record.
					// Use cancel instead.
					if (applyButton.isVisible())
					{
						restoreDelete = true;
						activateEditMode(true);
					}

					rightLayout.setVisible(true);

					selectFirstFieldAndShowTab();

					postNew(newEntity);

					buttonLayout.startNewPhase();

				}
				catch (Exception e)
				{
					handleConstraintViolationException(e);
				}
			}

		});
	}

	protected void selectFirstFieldAndShowTab()
	{
		for (Field<?> field : fieldGroup.getFields())
		{
			Component childField = field;

			for (int i = 0; i < 10; i++)
			{
				Component parentField = childField.getParent();
				if (parentField instanceof TabSheet)
				{
					((TabSheet) parentField).setSelectedTab(childField);
					break;
				}
				childField = parentField;
			}
			field.focus();
			break;
		}
	}

	protected String selectFirstErrorFieldAndShowTab()
	{
		String ret = "";
		int ctr = 0;
		for (Field<?> field : fieldGroup.getFields())
		{

			try
			{
				ctr++;
				field.validate();
			}
			catch (Exception e)
			{
				String message = "";
				if (e instanceof InvalidValueException)
				{
					message = ((InvalidValueException) e).getMessage();
					if (message == null)
					{
						for (InvalidValueException cause : ((InvalidValueException) e).getCauses())
						{
							message = cause.getMessage();
							if (message != null)
							{
								break;
							}
						}
					}
				}
				ret = field.getCaption() + "\n\n" + message;
				logger.warn(
						"Invalid Field...\n caption:'{}'\n type:{}\n fieldNumber: {}\n value: '{}'\n crud: {} ({})\n {}\n",
						field.getCaption(), field.getClass().getSimpleName(), ctr, field.getValue(), this.getClass()
								.getCanonicalName(), this.getClass().getSimpleName() + ".java:1", message);
				Component childField = field;

				for (int i = 0; i < 10; i++)
				{
					Component parentField = childField.getParent();
					if (parentField instanceof TabSheet)
					{
						((TabSheet) parentField).setSelectedTab(childField);
						break;
					}
					childField = parentField;
				}
				break;
			}

		}
		return ret;
	}

	/**
	 * you might want to implement this method in a child crud that needs to
	 * load some sort of list when a new entity is created based on the parent
	 * 
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 */
	protected void createNewEntity(E previousEntity) throws InstantiationException, IllegalAccessException
	{
		newEntity = container.createEntityItem(preNew(previousEntity));
	}

	/**
	 * override this method if you have child entities, you can use this
	 * opportunity to do some dirty hacking to populate fields
	 * 
	 * @param newEntity
	 */

	protected void postNew(EntityItem<E> newEntity)
	{

	}

	/**
	 * Override this method if you need to initialise the entity when a new
	 * record is created.
	 * 
	 * @param newEntity
	 * @return
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 */
	protected E preNew(E previousEntity) throws InstantiationException, IllegalAccessException
	{
		return entityClass.newInstance();
	}

	/**
	 * after making changes via JPA, to get the crud to see the changes call
	 * this method if needed.
	 * 
	 * beware it is a costly opperation.
	 * 
	 * commit the transaction and flush to db, then refresh the container
	 */
	public void reloadDataFromDB()
	{
		EntityManagerProvider.getEntityManager().getTransaction().commit();
		EntityManagerProvider.getEntityManager().getTransaction().begin();
		EntityManagerProvider.getEntityManager().flush();
		container.refresh();
	}

	/**
	 * for child cruds, they overload this to ensure that the minimum necessary
	 * filters are always applied.
	 */
	protected void resetFilters()
	{
		try
		{
			container.removeAllContainerFilters();
			((EntityTable<E>) this.entityTable).refreshRowCache();
		}
		catch (Exception e)
		{
			handleConstraintViolationException(e);

		}
	}

	public boolean isNew()
	{
		return this.newEntity != null;
	}

	public JPAContainer<E> getContainer()
	{
		return container;

	}

	// disabled as the save/cancel enable/disable is buggy
	@Override
	public void fieldGroupIsDirty(boolean b)
	{
		// saveButton.setEnabled(b);
		// cancelButton.setEnabled(b);
	}

	Set<ChildCrudListener<E>> getChildCrudListeners()
	{
		return Collections.unmodifiableSet(childCrudListeners);
	}

	protected DeleteVetoResponseData canDelete(E entity)
	{
		return new DeleteVetoResponseData(true);
	}

	private void notifyRowChangedListeners(E entity)
	{
		for (RowChangedListener<E> listener : rowChangedListeners)
		{
			listener.rowChanged(entity);
		}

	}

	public void addRowChangedListener(RowChangedListener<E> listener)
	{
		rowChangedListeners.add(listener);
	}

	public void removeRowChangedListener(RowChangedListener<E> listener)
	{
		rowChangedListeners.remove(listener);
	}

	@Override
	public void saveClicked()
	{
		// If interceptSaveClicked returns false then abort saving
		if (!interceptSaveClicked())
		{
			// return save/edit buttons to default settings
			buttonLayout.setDefaultState();
			return;
		}
		for (ChildCrudListener<E> child : getChildCrudListeners())
		{
			if (!child.interceptSaveClicked())
			{
				// return save/edit buttons to default settings
				buttonLayout.setDefaultState();
				return;
			}
		}

		save();
	}

	/**
	 * Override this method to intercept the save process after clicking the
	 * save button.
	 * 
	 * Return true if you would like the save action to proceed otherwise return
	 * false if you want to halt the save process.
	 * 
	 * When suppressing the action you should display a notification as to why
	 * you suppressed it.
	 * 
	 * @return whether to continue saving
	 */
	public boolean interceptSaveClicked()
	{
		return true;
	}

	public void setSearchFilterText(String string)
	{
		if (!searchField.getValue().equals(string))
		{
			entityTable.select(null);
			searchField.setValue(string);
			triggerFilter();
		}

	}

	public boolean isNewAllowed()
	{

		return !disallowNew;
	}

	public boolean hasDirtyChildren()
	{
		boolean dirty = false;
		for (ChildCrudListener<E> child : childCrudListeners)
		{
			dirty |= child.isDirty();
		}
		return dirty;
	}

}
