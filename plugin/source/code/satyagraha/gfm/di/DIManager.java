package code.satyagraha.gfm.di;

import static ch.lambdaj.collection.LambdaCollections.with;
import static code.satyagraha.gfm.di.DIUtils.getBundleClasses;
import static code.satyagraha.gfm.di.DIUtils.ComponentMatcher.isComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import org.eclipse.ui.IPageListener;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.BundleContext;

import ch.lambdaj.collection.LambdaGroup;
import ch.lambdaj.collection.LambdaList;
import code.satyagraha.gfm.di.Component.Scope;

public class DIManager {

    private static DIManager instance;

    private final boolean debugging;
    private final LambdaGroup<Class<?>> scopeComponentsGroup;
    private final Injector pluginInjector;
    private final Map<IWorkbenchPage, Injector> pageInjectorMap;
    private final WindowListener windowListener;

    // /////////////////////////////////////////////////////////////////////////
    // support classes
    // /////////////////////////////////////////////////////////////////////////

    private class PageListener implements IPageListener {

        void observing(IWorkbenchPage page) {
            debug("PageListener.observing: " + page);
            pageOpened(page);
        }

        @Override
        public void pageOpened(IWorkbenchPage page) {
            debug("PageListener.pageOpened: " + page);
            if (!pageInjectorMap.containsKey(page)) {
                Injector pageInjector = new Injector(pluginInjector, scopeComponentsGroup.find(Scope.PAGE));
                pageInjector.addInstance(page);
                pageInjectorMap.put(page, pageInjector);
            }
        }

        @Override
        public void pageActivated(IWorkbenchPage page) {
            debug("PageListener.pageActivated: " + page);
        }

        @Override
        public void pageClosed(IWorkbenchPage page) {
            debug("PageListener.pageClosed: " + page);
            pageInjectorMap.remove(page);
        }

    }

    private class WindowListener implements IWindowListener {

        private final Map<IWorkbenchWindow, PageListener> pageListeners;

        WindowListener() {
            pageListeners = new IdentityHashMap<IWorkbenchWindow, PageListener>();
        }

        void observing(IWorkbenchWindow window) {
            debug("WindowListener.observing: " + window);
            windowOpened(window);
        }

        @Override
        public void windowOpened(IWorkbenchWindow window) {
            debug("WindowListener.windowOpened: " + window);
            if (!pageListeners.containsKey(window)) {
                PageListener pageListener = new PageListener();
                pageListeners.put(window, pageListener);
                window.addPageListener(pageListener);
                for (IWorkbenchPage page : window.getPages()) {
                    pageListener.observing(page);
                }
            }
        }

        @Override
        public void windowActivated(IWorkbenchWindow window) {
            debug("WindowListener.windowActivated: " + window);
        }

        @Override
        public void windowDeactivated(IWorkbenchWindow window) {
            debug("WindowListener.windowDeactivated: " + window);
        }

        @Override
        public void windowClosed(IWorkbenchWindow window) {
            debug("WindowListener.windowClosed: " + window);
            PageListener pageListener = pageListeners.get(window);
            if (pageListener != null) {
                pageListeners.remove(window);
                window.removePageListener(pageListener);
            }
        }

    }

    // /////////////////////////////////////////////////////////////////////////
    // class implementation
    // /////////////////////////////////////////////////////////////////////////

    private DIManager(BundleContext bundleContext, String packagePrefix, boolean debugging) {
        this.debugging = debugging;

        Collection<Class<?>> components = with(getBundleClasses(bundleContext.getBundle(), packagePrefix)).retain(isComponent);

        scopeComponentsGroup = with(components).group(new DIUtils.ScopeGroupCondition());
        for (Scope scope : Scope.values()) {
            LambdaList<Class<?>> scopeComponents = scopeComponentsGroup.find(scope);
            debug("scope: " + scope + " scopeComponents: " + scopeComponents);
        }

        pluginInjector = new Injector(scopeComponentsGroup.find(Scope.PLUGIN));

        pageInjectorMap = Collections.synchronizedMap(new IdentityHashMap<IWorkbenchPage, Injector>());

        windowListener = new WindowListener();
        PlatformUI.getWorkbench().addWindowListener(windowListener);
        for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
            windowListener.observing(window);
        }
    }

    public Injector getInjector(Component.Scope scope) {
        switch (scope) {
        
        case PLUGIN:
            return pluginInjector;
            
        case PAGE:
            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
            Injector pageInjector = pageInjectorMap.get(page);
            if (pageInjector == null) {
                throw new IllegalStateException("unable to locate pageInjector for page: " + page);
            }
            return pageInjector;
            
        default:
            throw new IllegalArgumentException("unexpected scope: " + scope);
        }
    }

    private void close() {
        PlatformUI.getWorkbench().removeWindowListener(windowListener);
    }

    private void debug(String message) {
        if (debugging) {
            System.out.println(message);
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // static methods
    // /////////////////////////////////////////////////////////////////////////

    public static void start(BundleContext bundleContext, String packagePrefix, boolean debugging) {
        instance = new DIManager(bundleContext, packagePrefix, debugging);
    }

    public static void stop() {
        instance.close();
        instance = null;
    }

    public static DIManager getDefault() {
        return instance;
    }

}
