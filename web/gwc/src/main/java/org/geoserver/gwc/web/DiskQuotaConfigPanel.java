package org.geoserver.gwc.web;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.Radio;
import org.apache.wicket.markup.html.form.RadioGroup;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.model.StringResourceModel;
import org.geoserver.gwc.GWC;
import org.geotools.util.logging.Logging;
import org.geowebcache.diskquota.DiskQuotaConfig;
import org.geowebcache.diskquota.ExpirationPolicy;
import org.geowebcache.diskquota.storage.Quota;
import org.geowebcache.diskquota.storage.StorageUnit;

public class DiskQuotaConfigPanel extends Panel {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logging.getLogger(DiskQuotaConfigPanel.class);

    private IModel<GWC> gwcModel;

    @SuppressWarnings({ "rawtypes" })
    public DiskQuotaConfigPanel(final String id, final Form form,
            final IModel<DiskQuotaConfig> diskQuotaConfigModel, final IModel<GWC> gwcModel,
            IModel<Double> configQuotaValueModel, IModel<StorageUnit> configQuotaUnitModel) {

        super(id);
        this.gwcModel = gwcModel;

        addDiskQuotaIntegrationEnablement(diskQuotaConfigModel);

        addDiskBlockSizeConfig(diskQuotaConfigModel);

        addCleanUpFrequencyConfig(diskQuotaConfigModel);

        addGlobalQuotaConfig(diskQuotaConfigModel, configQuotaValueModel, configQuotaUnitModel);

        addGlobalExpirationPolicyConfig(diskQuotaConfigModel);

    }

    private void addGlobalQuotaConfig(final IModel<DiskQuotaConfig> diskQuotaModel,
            IModel<Double> quotaValueModel, IModel<StorageUnit> unitModel) {

        final IModel<Quota> globalQuotaModel = new LoadableDetachableModel<Quota>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected Quota load() {
                return getGWC().getGlobalQuota();
            }
        };
        final IModel<Quota> globalUsedQuotaModel = new LoadableDetachableModel<Quota>() {
            private static final long serialVersionUID = 1L;

            @Override
            protected Quota load() {
                return getGWC().getGlobalUsedQuota();
            }
        };

        Object[] progressMessageParams = { globalUsedQuotaModel.getObject().toNiceString(),
                globalQuotaModel.getObject().toNiceString() };
        IModel<String> progressMessageModel = new StringResourceModel(
                "GWCSettingsPage.usedQuotaMessage", null, progressMessageParams);
        addGlobalQuotaStatusBar(globalQuotaModel, globalUsedQuotaModel, progressMessageModel);

        TextField<Double> quotaValue = new TextField<Double>("globalQuota", quotaValueModel);
        quotaValue.setRequired(true);
        add(quotaValue);

        List<? extends StorageUnit> units = Arrays.asList(StorageUnit.MiB, StorageUnit.GiB,
                StorageUnit.TiB);
        DropDownChoice<StorageUnit> quotaUnitChoice;
        quotaUnitChoice = new DropDownChoice<StorageUnit>("globalQuotaUnits", unitModel, units);
        add(quotaUnitChoice);
    }

    private void addGlobalExpirationPolicyConfig(final IModel<DiskQuotaConfig> diskQuotaModel) {
        IModel<ExpirationPolicy> globalQuotaPolicyModel = new PropertyModel<ExpirationPolicy>(
                diskQuotaModel, "globalExpirationPolicyName");

        RadioGroup<ExpirationPolicy> globalQuotaPolicy;
        globalQuotaPolicy = new RadioGroup<ExpirationPolicy>("globalQuotaExpirationPolicy",
                globalQuotaPolicyModel);
        add(globalQuotaPolicy);

        IModel<ExpirationPolicy> lfuModel = new Model<ExpirationPolicy>(ExpirationPolicy.LFU);
        IModel<ExpirationPolicy> lruModel = new Model<ExpirationPolicy>(ExpirationPolicy.LRU);
        
        Radio<ExpirationPolicy> globalQuotaPolicyLFU;
        Radio<ExpirationPolicy> globalQuotaPolicyLRU;
        globalQuotaPolicyLFU = new Radio<ExpirationPolicy>("globalQuotaPolicyLFU", lfuModel);
        globalQuotaPolicyLRU = new Radio<ExpirationPolicy>("globalQuotaPolicyLRU", lruModel);

        globalQuotaPolicy.add(globalQuotaPolicyLFU);
        globalQuotaPolicy.add(globalQuotaPolicyLRU);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void addCleanUpFrequencyConfig(final IModel<DiskQuotaConfig> diskQuotaModel) {

        final DiskQuotaConfig diskQuotaConfig = diskQuotaModel.getObject();

        int frequency = diskQuotaConfig.getCacheCleanUpFrequency();
        TimeUnit unit = diskQuotaConfig.getCacheCleanUpUnits();
        if (TimeUnit.SECONDS != unit) {
            frequency = (int) TimeUnit.SECONDS.convert(frequency, unit);
            diskQuotaConfig.setCacheCleanUpFrequency(frequency);
            diskQuotaConfig.setCacheCleanUpUnits(TimeUnit.SECONDS);
        }

        IModel<Integer> cleanUpFreqModel;
        cleanUpFreqModel = new PropertyModel<Integer>(diskQuotaModel, "cacheCleanUpFrequency");
        TextField<Integer> cleanUpFreq = new TextField<Integer>("cleanUpFreq", cleanUpFreqModel);
        cleanUpFreq.setRequired(true);
        cleanUpFreq.add(new AttributeModifier("title", true, new StringResourceModel(
                "GWCSettingsPage.cleanUpFreq.title", (Component) null, null)));
        add(cleanUpFreq);
        {
            Date lastRun = diskQuotaConfig.getLastCleanUpTime();
            String resourceId;
            HashMap<String, String> params = new HashMap<String, String>();
            if (lastRun == null) {
                resourceId = "GWCSettingsPage.cleanUpLastRunNever";
            } else {
                resourceId = "GWCSettingsPage.cleanUpLastRun";
                long timeAgo = (System.currentTimeMillis() - lastRun.getTime()) / 1000;
                String timeUnits = "s";
                if (timeAgo > 60 * 60 * 24) {
                    timeUnits = "d";
                    timeAgo /= 60 * 60 * 24;
                } else if (timeAgo > 60 * 60) {
                    timeUnits = "h";
                    timeAgo /= 60 * 60;
                } else if (timeAgo > 60) {
                    timeUnits = "m";
                    timeAgo /= 60;
                }
                params.put("x", String.valueOf(timeAgo));
                params.put("timeUnit", timeUnits);
            }
            IModel<String> lastRunModel = new StringResourceModel(resourceId, this, new Model(
                    params));
            add(new Label("GWCSettingsPage.cleanUpLastRun", lastRunModel));
        }
    }

    private void addDiskBlockSizeConfig(final IModel<DiskQuotaConfig> diskQuotaModel) {
        IModel<Integer> blockSizeModel;
        blockSizeModel = new PropertyModel<Integer>(diskQuotaModel, "diskBlockSize");
        TextField<Integer> diskBlockSize = new TextField<Integer>("diskBlockSize", blockSizeModel);
        diskBlockSize.setRequired(true);
        diskBlockSize.add(new AttributeModifier("title", true, new StringResourceModel(
                "GWCSettingsPage.diskBlockSize.title", (Component) null, null)));
        add(diskBlockSize);
    }

    private void addDiskQuotaIntegrationEnablement(IModel<DiskQuotaConfig> diskQuotaModel) {
        IModel<Boolean> quotaEnablementModel = new PropertyModel<Boolean>(diskQuotaModel, "enabled");
        CheckBox diskQuotaIntegration = GWCSettingsPage.checkbox("enableDiskQuota",
                quotaEnablementModel, "GWCSettingsPage.enableDiskQuota.title");
        add(diskQuotaIntegration);
    }

    private void addGlobalQuotaStatusBar(final IModel<Quota> globalQuotaModel,
            final IModel<Quota> globalUsedQuotaModel, IModel<String> progressMessageModel) {

        Quota limit = globalQuotaModel.getObject();
        Quota used = globalUsedQuotaModel.getObject();

        BigInteger limitValue = limit.getBytes();
        BigInteger usedValue = used.getBytes();

        StorageUnit bestUnitForLimit = StorageUnit.bestFit(limitValue);
        StorageUnit bestUnitForUsed = StorageUnit.bestFit(usedValue);

        StorageUnit biggerUnit = bestUnitForLimit.compareTo(bestUnitForUsed) > 0 ? bestUnitForLimit
                : bestUnitForUsed;

        BigDecimal showLimit = StorageUnit.B.convertTo(new BigDecimal(limitValue), biggerUnit);
        BigDecimal showUsed = StorageUnit.B.convertTo(new BigDecimal(usedValue), biggerUnit);

        final IModel<Number> limitModel = new Model<Number>(showLimit);
        final IModel<Number> usedModel = new Model<Number>(showUsed);

        StatusBar statusBar = new StatusBar("globalQuotaProgressBar", limitModel, usedModel,
                progressMessageModel);

        add(statusBar);
    }

    private GWC getGWC() {
        return gwcModel.getObject();
    }
}
