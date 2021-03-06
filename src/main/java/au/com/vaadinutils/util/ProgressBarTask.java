package au.com.vaadinutils.util;

import com.vaadin.ui.UI;

public abstract class ProgressBarTask<T>
{
	private ProgressTaskListener<T> listener;

	final UI ui;
	public ProgressBarTask(ProgressTaskListener<T> listener)
	{
		this.listener = listener;
		ui = UI.getCurrent();
	}

	abstract public void run();

	protected void taskComplete(final int sent)
	{
		ui.accessSynchronously(new Runnable()
		{

			@Override
			public void run()
			{
				listener.taskComplete(sent);
			}

		});

	}

	public void taskProgress(final int count, final int max, final T status)
	{
		ui.accessSynchronously(new Runnable()
		{

			@Override
			public void run()
			{
				listener.taskProgress(count, max, status);
			}
		}

		);
	}

	public void taskItemError(final T status)
	{
		ui.accessSynchronously(new Runnable()
		{

			@Override
			public void run()
			{
				listener.taskItemError(status);
			}
		}

		);

	}

	public void taskException(final Exception e)
	{
		ui.accessSynchronously(new Runnable()
		{

			@Override
			public void run()
			{
				listener.taskException(e);
			}
		}

		);

	}
}
