package io.github.alejorom.demandflow.tariffs.web;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService;
import io.github.alejorom.demandflow.tariffs.application.HourlyRateService.RateView;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/** HU-002; registration is gated by the existing persistence module configuration. */
@Controller
@RequestMapping("/tariffs/rates")
public class RateController {
    private final HourlyRateService rates;
    private final TariffPeriodService periods;
    private final CatalogFailureTranslator failures;

    public RateController(HourlyRateService rates, TariffPeriodService periods, CatalogFailureTranslator failures) {
        this.rates = rates;
        this.periods = periods;
        this.failures = failures;
    }

    @InitBinder("form")
    void formBinding(WebDataBinder binder) {
        Class<?> type = binder.getTarget() != null ? binder.getTarget().getClass() : binder.getTargetType().resolve();
        if (StateForm.class.equals(type)) { CatalogWebSupport.bindOnly(binder, "expectedVersion"); }
        else if (RateForm.Edit.class.equals(type)) { CatalogWebSupport.bindOnly(binder, "amountCOP", "expectedVersion"); }
        else { CatalogWebSupport.bindOnly(binder, "amountCOP", "periodId", "typeId"); }
    }

    @GetMapping
    ModelAndView list() { return new ModelAndView("tariffs/rates/list", "rates", rates.list()); }

    @GetMapping("/new")
    ModelAndView newRate() {
        return options(new ModelAndView("tariffs/rates/form", "form", new RateForm.Create()).addObject("editing", false));
    }

    @GetMapping("/{id}")
    ModelAndView detail(@PathVariable Long id) {
        var rate = rates.get(id);
        var form = new StateForm();
        form.setExpectedVersion(rate.version());
        return new ModelAndView("tariffs/rates/detail", "rate", rate).addObject("form", form);
    }

    @GetMapping("/{id}/edit")
    ModelAndView edit(@PathVariable Long id) {
        var rate = rates.get(id);
        var form = new RateForm.Edit();
        form.setAmountCOP(rate.amountCop().toPlainString());
        form.setExpectedVersion(rate.version());
        return new ModelAndView("tariffs/rates/form", "form", form).addObject("editing", true).addObject("rate", rate);
    }

    @PostMapping
    ModelAndView create(@Valid @ModelAttribute("form") RateForm.Create form, BindingResult errors,
                        HttpServletRequest request) {
        CatalogWebSupport.rejectUnexpected(request, errors, "amountCOP", "periodId", "typeId");
        var page = options(CatalogWebSupport.form("tariffs/rates/form", errors).addObject("editing", false));
        return CatalogWebSupport.submit(page, errors, failures, () -> saved(rates.create(form.getPeriodId(), form.getTypeId(), form.amount())));
    }

    @PostMapping("/{id}/edit")
    ModelAndView update(@PathVariable Long id, @Valid @ModelAttribute("form") RateForm.Edit form,
                        BindingResult errors, HttpServletRequest request) {
        CatalogWebSupport.rejectUnexpected(request, errors, "amountCOP", "expectedVersion");
        var page = CatalogWebSupport.form("tariffs/rates/form", errors)
                .addObject("editing", true).addObject("rate", rates.get(id));
        return CatalogWebSupport.submit(page, errors, failures, () -> saved(rates.correctAmount(id, form.getExpectedVersion(), form.amount())));
    }

    @PostMapping("/{id}/deactivate")
    ModelAndView deactivate(@PathVariable Long id, @Valid @ModelAttribute("form") StateForm form,
                            BindingResult errors, HttpServletRequest request) {
        return changeState(id, form, errors, request, false);
    }

    @PostMapping("/{id}/reactivate")
    ModelAndView reactivate(@PathVariable Long id, @Valid @ModelAttribute("form") StateForm form,
                            BindingResult errors, HttpServletRequest request) {
        return changeState(id, form, errors, request, true);
    }

    private ModelAndView changeState(Long id, StateForm form, BindingResult errors,
                                     HttpServletRequest request, boolean active) {
        CatalogWebSupport.rejectUnexpected(request, errors, "expectedVersion");
        var page = CatalogWebSupport.form("tariffs/rates/detail", errors).addObject("rate", rates.get(id));
        return CatalogWebSupport.submit(page, errors, failures, () -> saved(active ? rates.reactivate(id, form.getExpectedVersion())
                : rates.deactivate(id, form.getExpectedVersion())));
    }

    private ModelAndView options(ModelAndView page) {
        return page.addObject("periods", periods.list()).addObject("types", rates.listTypes());
    }

    private ModelAndView saved(RateView rate) { return new ModelAndView("redirect:/tariffs/rates/" + rate.id()); }
}
