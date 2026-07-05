package com.ragpro.lovemaster.payment;

import com.ragpro.lovemaster.config.LoveMasterProperties;
import com.ragpro.lovemaster.model.LoveCourseInfo;
import com.ragpro.lovemaster.rag.LoveKnowledgeCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseProductService {

    private final LoveMasterProperties properties;
    private final LoveKnowledgeCatalogService catalogService;

    public boolean isPaymentEnabled() {
        if (!properties.getPayment().isEnabled()) {
            return false;
        }
        if (StringUtils.hasText(properties.getPayment().getStripe().getSecretKey())) {
            return true;
        }
        if (properties.getPayment().getCourses().values().stream()
                .anyMatch(c -> StringUtils.hasText(c.getPaymentLinkUrl()))) {
            return true;
        }
        return properties.getPayment().getDomestic().isEnabled();
    }

    public boolean hasCheckoutApi() {
        return StringUtils.hasText(properties.getPayment().getStripe().getSecretKey());
    }

    public Optional<CourseProduct> findProduct(String courseId) {
        LoveMasterProperties.CourseProductProperties config =
                properties.getPayment().getCourses().get(courseId);
        if (config == null) {
            return Optional.empty();
        }
        String name = StringUtils.hasText(config.getName())
                ? config.getName()
                : catalogService.findDocumentTitle(courseId);
        String currency = StringUtils.hasText(config.getCurrency())
                ? config.getCurrency()
                : properties.getPayment().getStripe().getCurrency();

        return Optional.of(CourseProduct.builder()
                .courseId(courseId)
                .name(name)
                .stripePriceId(config.getStripePriceId())
                .paymentLinkUrl(config.getPaymentLinkUrl())
                .amountCents(config.getAmountCents())
                .currency(currency)
                .build());
    }

    public List<LoveCourseInfo> listPurchasableCourses() {
        List<LoveCourseInfo> courses = new ArrayList<>();
        for (Map.Entry<String, LoveMasterProperties.CourseProductProperties> entry
                : properties.getPayment().getCourses().entrySet()) {
            findProduct(entry.getKey())
                    .filter(this::isProductPurchasable)
                    .ifPresent(product -> courses.add(toCourseInfo(product)));
        }
        return courses;
    }

    public LoveCourseInfo enrich(LoveCourseInfo course) {
        if (course == null || !StringUtils.hasText(course.getId())) {
            return course;
        }
        return findProduct(course.getId())
                .filter(this::isProductPurchasable)
                .map(product -> applyProduct(course, product))
                .orElse(course);
    }

    public LoveCourseInfo toCourseInfo(CourseProduct product) {
        return applyProduct(LoveCourseInfo.builder().build(), product);
    }

    public boolean isProductPurchasable(CourseProduct product) {
        if (!properties.getPayment().isEnabled()) {
            return false;
        }
        if (StringUtils.hasText(product.getPaymentLinkUrl())) {
            return true;
        }
        if (hasCheckoutApi() && product.getAmountCents() > 0) {
            return true;
        }
        return properties.getPayment().getDomestic().isEnabled() && product.getAmountCents() > 0;
    }

    private LoveCourseInfo applyProduct(LoveCourseInfo course, CourseProduct product) {
        course.setId(product.getCourseId());
        course.setName(product.getName());
        course.setPrice(formatPrice(product.getAmountCents(), product.getCurrency()));
        course.setPurchasable(true);
        course.setStripePriceId(product.getStripePriceId());
        course.setPaymentLinkUrl(product.getPaymentLinkUrl());
        course.setAmountCents(product.getAmountCents());
        course.setCurrency(product.getCurrency());
        return course;
    }

    private String formatPrice(long amountCents, String currency) {
        if (amountCents <= 0) {
            return "";
        }
        if ("cny".equalsIgnoreCase(currency)) {
            return (amountCents / 100) + "元";
        }
        return String.format("%.2f %s", amountCents / 100.0, currency.toUpperCase());
    }
}
