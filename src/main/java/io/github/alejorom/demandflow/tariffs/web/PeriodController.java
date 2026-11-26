package io.github.alejorom.demandflow.tariffs.web;

import io.github.alejorom.demandflow.tariffs.application.CatalogFailureTranslator;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService;
import io.github.alejorom.demandflow.tariffs.application.TariffPeriodService.PeriodView;
import io.github.alejorom.demandflow.tariffs.domain.CatalogConflictException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/** HU-001; registered only by the existing persistence-scoped module scan (T8). */
@Controller
@RequestMapping("/tariffs/periods")
public class PeriodController {
    private final TariffPeriodService periods;
    private final CatalogFailureTranslator failures;

    public PeriodController(TariffPeriodService periods, CatalogFailureTranslator failures) {
        this.periods = periods;
        this.failures = failures;
    }

    @InitBinder("form")
    void formBinding(WebDataBinder binder) {
        Class<?> type = binder.getTarget() != null ? binder.getTarget().getClass()
                : binder.getTargetType().resolve(); // InitBinder can run before constructor binding.
        if (StateForm.class.equals(type)) { CatalogWebSupport.bindOnly(binder, "expectedVersion"); }
        else if (PeriodForm.Edit.class.equals(type)) {
            CatalogWebSupport.bindOnly(binder, "label", "startDate", "endDate", "expectedVersion");
        } else { CatalogWebSupport.bindOnly(binder, "label", "startDate", "endDate"); }
    }

    @GetMapping
    ModelAndView list() { return new ModelAndView("tariffs/periods/list", "periods", periods.list()); }

    @GetMapping("/new")
    ModelAndView newPeriod() {
        return new ModelAndView("tariffs/periods/form", "form", new PeriodForm()).addObject("editing", false);
    }

    @GetMapping("/{id}")
    ModelAndView detail(@PathVariable Long id) {
        var period = periods.get(id);
        var form = new StateForm();
        form.setExpectedVersion(period.version());
        return new ModelAndView("tariffs/periods/detail", "period", period).addObject("form", form);
    }

    @GetMapping("/{id}/edit")
    ModelAndView edit(@PathVariable Long id) {
        var period = periods.get(id);
        var form = new PeriodForm.Edit();
        form.setLabel(period.label());
        form.setStartDate(period.startDate().toString());
        form.setEndDate(period.endDate().toString());
        form.setExpectedVersion(period.version());
        return new ModelAndView("tariffs/periods/form", "form", form)
                .addObject("period", period).addObject("editing", true);
    }

    @PostMapping
    ModelAndView create(@Valid @ModelAttribute("form") PeriodForm form, BindingResult errors,
                        HttpServletRequest request) {
        CatalogWebSupport.rejectUnexpected(request, errors, "label", "startDate", "endDate");
        var page = CatalogWebSupport.form("tariffs/periods/form", errors).addObject("editing", false);
        if (errors.hasErrors()) { return page; }
        try { return saved(periods.create(form.normalizedLabel(), form.start(), form.end())); }
        catch (CatalogConflictException | OptimisticLockingFailureException failure) {
            return CatalogWebSupport.conflict(page, errors, failure, failures);
        }
    }

    @PostMapping("/{id}/edit")
    ModelAndView update(@PathVariable Long id, @Valid @ModelAttribute("form") PeriodForm.Edit form,
                        BindingResult errors, HttpServletRequest request) {
        CatalogWebSupport.rejectUnexpected(request, errors, "label", "startDate", "endDate", "expectedVersion");
        var page = CatalogWebSupport.form("tariffs/periods/form", errors)
                .addObject("editing", true).addObject("period", periods.get(id));
        if (errors.hasErrors()) { return page; }
        try { return saved(periods.edit(id, form.getExpectedVersion(), form.normalizedLabel(), form.start(), form.end())); }
        catch (CatalogConflictException | OptimisticLockingFailureException failure) {
            return CatalogWebSupport.conflict(page, errors, failure, failures);
        }
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
        var page = CatalogWebSupport.form("tariffs/periods/detail", errors).addObject("period", periods.get(id));
        if (errors.hasErrors()) { return page; }
        try { return saved(active ? periods.reactivate(id, form.getExpectedVersion())
                : periods.deactivate(id, form.getExpectedVersion())); }
        catch (CatalogConflictException | OptimisticLockingFailureException failure) {
            return CatalogWebSupport.conflict(page, errors, failure, failures);
        }
    }

    // Provisional minimum of 8.4 for the reference cases of 8.1; no general persistence/HTTP adapter.
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String missingReference() { return "error"; }

    private ModelAndView saved(PeriodView period) {
        return new ModelAndView("redirect:/tariffs/periods/" + period.id());
    }
}
