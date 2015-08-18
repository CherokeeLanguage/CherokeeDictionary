package com.cherokeelessons.dict.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.gwtbootstrap3.client.ui.Heading;
import org.gwtbootstrap3.client.ui.Label;
import org.gwtbootstrap3.client.ui.Panel;
import org.gwtbootstrap3.client.ui.PanelBody;
import org.gwtbootstrap3.client.ui.PanelHeader;
import org.gwtbootstrap3.client.ui.constants.HeadingSize;
import org.gwtbootstrap3.client.ui.constants.LabelType;
import org.gwtbootstrap3.client.ui.constants.PanelType;
import org.gwtbootstrap3.client.ui.gwt.HTMLPanel;

import com.cherokeelessons.dict.client.ClientDictionary;
import com.cherokeelessons.dict.client.DictionaryApplication;
import com.cherokeelessons.dict.engine.Affixes.AffixResult;
import com.cherokeelessons.dict.events.AbortEvent;
import com.cherokeelessons.dict.events.AddAnalysisPanelEvent;
import com.cherokeelessons.dict.events.AnalysisCompleteEvent;
import com.cherokeelessons.dict.events.AnalyzeEvent;
import com.cherokeelessons.dict.events.Binders;
import com.cherokeelessons.dict.events.ClearResultsEvent;
import com.cherokeelessons.dict.events.ResetInputEvent;
import com.cherokeelessons.dict.events.UiEnableEvent;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.binder.EventHandler;
import commons.lang3.StringUtils;

public class DoAnalysis {
	static {
	}

	private final EventBus eventBus;

	public DoAnalysis(EventBus eventBus) {
		Binders.binder_analyzer.bindEventHandlers(this, eventBus);
		GWT.log("#" + String.valueOf(Binders.binder_analyzer));
		this.eventBus = eventBus;
	}

	@EventHandler
	public void abort(AbortEvent event) {
		GWT.log(this.getClass().getSimpleName()+"#Event#abort");
	}

	@EventHandler
	public void analyze(final AnalyzeEvent event) {
		GWT.log(this.getClass().getSimpleName()+"#Event#analyze");
		String value = StringUtils.strip(event.query);
		value = value.replaceAll("[^Ꭰ-Ᏼ0-9]", " ");
		value = value.replaceAll(" +", " ");
		if (StringUtils.isBlank(value)) {
			eventBus.fireEvent(new ResetInputEvent());
			eventBus.fireEvent(new UiEnableEvent(true));
			return;
		}
		eventBus.fireEvent(new UiEnableEvent(false));
		eventBus.fireEvent(new ClearResultsEvent());
		final List<ScheduledCommand> cmds = new ArrayList<>();
		List<String> words = new ArrayList<>(Arrays.asList(StringUtils
				.split(value)));

		for (final String word : words) {
			cmds.add(new ScheduledCommand() {
				@Override
				public void execute() {
					PanelType type = PanelType.DEFAULT;
					SafeHtmlBuilder shb = new SafeHtmlBuilder();
					List<AffixResult> matched = SuffixGuesser.INSTANCE
							.getMatches(word);
					if (matched.size() != 0) {
						type = PanelType.SUCCESS;
					}

					Set<String> already = new HashSet<>();
					Iterator<AffixResult> imatch = matched.iterator();
					while (imatch.hasNext()) {
						AffixResult next = imatch.next();
						String combo = next.suffix + "|" + next.stem.toString();
						if (already.contains(combo)) {
							imatch.remove();
						}
						already.add(combo);
					}

					StringBuilder affixedStem = new StringBuilder();
					String innerstem = "";
					for (AffixResult match : matched) {
						already.clear();
						String matchDesc = match.desc + "{" + match.suffix
								+ "}";
						if (!already.contains(matchDesc)) {
							shb.appendEscapedLines(matchDesc);
							shb.appendHtmlConstant("<br/>");
						}
						already.add(matchDesc);
						String info = ClientDictionary.INSTANCE
								.guess(match.stem);
						if (!StringUtils.isBlank(info)) {
							shb.appendHtmlConstant("<span style='color: navy; font-weight: bold;'>");
							shb.appendEscapedLines(info.replace("|", "\n"));
							shb.appendHtmlConstant("</span><br/>");
						}
						// shb.appendHtmlConstant("<br />");
						if (match.desc.contains("*")) {
							type = PanelType.DANGER;
						}
						already.add(match.stem);
						affixedStem.insert(0, "[+" + match.suffix + "]");
						innerstem = match.stem;
					}
					affixedStem.insert(0, innerstem);
					SafeHtmlBuilder affixedStemHtml = new SafeHtmlBuilder();

					String info = ClientDictionary.INSTANCE.guess(word);
					if (!StringUtils.isBlank(info)) {
						affixedStemHtml
								.appendHtmlConstant("<span style='color: navy; font-weight: bold;'>");
						affixedStemHtml.appendEscapedLines(info.replace("|",
								"\n"));
						affixedStemHtml.appendHtmlConstant("</span><br/>");
					}

					if (affixedStem.length() != 0) {
						affixedStemHtml
								.appendHtmlConstant("<span style='font-style: italic; font-weight: bold;'>");
						affixedStemHtml.appendEscaped(affixedStem.toString());
						affixedStemHtml.appendHtmlConstant("</span><br/>");
					}
					affixedStemHtml.append(shb.toSafeHtml());

					final Panel p = new Panel(type);
					Style style = p.getElement().getStyle();
					style.setWidth((DictionaryApplication.WIDTH - 20) / 3 - 5,
							Unit.PX);
					style.setDisplay(Display.INLINE_BLOCK);
					style.setMarginRight(5, Unit.PX);
					style.setVerticalAlign(Style.VerticalAlign.TOP);
					style.setMarginBottom(5, Unit.PX);
					PanelHeader ph = new PanelHeader();
					Heading h = new Heading(HeadingSize.H5);
					h.setText(word);
					ph.add(h);
					h.getElement().getStyle().setDisplay(Display.INLINE_BLOCK);
					Label source = new Label(LabelType.INFO);
					ph.add(source);
					source.getElement().getStyle().setFloat(Style.Float.RIGHT);
					source.setText("[analysis]");
					PanelBody pb = new PanelBody();

					HTMLPanel hp = new HTMLPanel(affixedStemHtml.toSafeHtml());

					// PanelFooter pf = new PanelFooter();
					// Button dismiss = new Button("DISMISS");
					// dismiss.addClickHandler(new ClickHandler() {
					// @Override
					// public void onClick(ClickEvent event) {
					// eventBus.fireEvent(new RemovePanelEvent(p));
					// }
					// });
					// pf.add(dismiss);
					p.add(ph);
					p.add(pb);
					// p.add(pf);

					pb.add(hp);
					eventBus.fireEvent(new AddAnalysisPanelEvent(p));
				}
			});
		}
		cmds.add(new ScheduledCommand() {
			@Override
			public void execute() {
				eventBus.fireEvent(new AnalysisCompleteEvent());
			}
		});
		Scheduler.get().scheduleDeferred(new ScheduledCommand() {
			@Override
			public void execute() {
				if (cmds.size() > 0) {
					ScheduledCommand cmd = cmds.get(0);
					cmds.remove(0);
					Scheduler.get().scheduleDeferred(cmd);
					Scheduler.get().scheduleDeferred(this);
					return;
				}
			}
		});
	}
}
