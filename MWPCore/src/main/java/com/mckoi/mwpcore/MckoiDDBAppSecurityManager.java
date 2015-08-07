/**
 * com.mckoi.mwpcore.MckoiDDBAppSecurityManager  May 30, 2010
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2012  Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this program.  If not, see ( http://www.gnu.org/licenses/ ) or
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 *
 * Change Log:
 *
 *
 */

package com.mckoi.mwpcore;

import com.mckoi.webplatform.MckoiDDBWebPermission;
import com.mckoi.webplatform.impl.PlatformContextImpl;
import java.lang.reflect.Member;
import java.security.*;
import java.text.MessageFormat;
import java.util.ArrayList;

/**
 * The system security manager for the Mckoi web platform.
 *
 * @author Tobias Downer
 */

final class MckoiDDBAppSecurityManager extends SecurityManager {


  private static final RuntimePermission MODIFY_THREAD_PERMISSION =
          new RuntimePermission("modifyThread");

  private static final RuntimePermission MODIFY_THREADGROUP_PERMISSION =
          new RuntimePermission("modifyThreadGroup");

  private static final RuntimePermission ACCESS_DECLARED_MEMBERS_PERMISSION =
          new RuntimePermission("accessDeclaredMembers");
  private static final MckoiDDBWebPermission ACCESS_MEMBERS_PERMISSION =
          new MckoiDDBWebPermission("accessPublicMembers");

  private static final Class[] make_classes;

  // Trusted system class loaders,
  private static ClassLoader[] trusted_class_loaders;

  static {
    // To prevent class circularity errors we make sure certain classes are
    // created.
    make_classes = new Class[] {
      SMTrustedAction.class,
      TrustedGetDomain.class,
      TrustedGetClassLoader.class,
      TrustedGetSystemClassLoader.class,
      AccessController.class,
      PrivilegedAction.class,
      PlatformContextImpl.class,
      Policy.class,
      ProtectionDomain.class,
      SecurityException.class
    };
  }

  /**
   * Builds the array of trusted class loaders. Should be called before the
   * security manager is set in the system.
   */
  static void makeTrustedClassLoaders(ClassLoader top_level_priv_cl) {
    if (trusted_class_loaders == null) {
      // Get the system class loader and descend through the parents adding
      // each class loader to the list,
      ArrayList<ClassLoader> loaders = new ArrayList<>();
      ClassLoader system_class_loader = top_level_priv_cl;
      while (system_class_loader != null) {
        loaders.add(system_class_loader);
        system_class_loader = system_class_loader.getParent();
      }

      trusted_class_loaders = loaders.toArray(new ClassLoader[loaders.size()]);
    }
  }

  /**
   * Classes generally allowed to be loaded by the user permission.
   */
  private final ClassNameValidator user_allowed_sys_classes;

  /**
   * Constructor.
   */
  MckoiDDBAppSecurityManager(ClassNameValidator allowed_sys_classes) {
    this.user_allowed_sys_classes = allowed_sys_classes;
  }

  
  /**
   * Returns true if the class loader is in the list of trusted/protected
   * class loaders.
   */
  private boolean isTrustedClassLoader(ClassLoader cl) {
    // The bootstrap class loader,
    if (cl == null) {
      return true;
    }
    // Is cl in the set of trusted class loaders?
    for (ClassLoader trusted_cl : trusted_class_loaders) {
      if (cl == trusted_cl) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns true if the class loader that created the class is trusted.
   */
  private boolean fromTrustedClassLoader(Class c) {
    // NOTE: We defer these operations through a trusted action object.
    //   Technically, this isn't necessary because of the spec of
    //   'getClassLoader' and 'getSystemClassLoader' says it will not perform
    //   a security check if the callee class is from the same class loader,
    //   but since we may decide to move the class loaders around we keep this
    //   indirection so things stay stable (I don't think the overhead is too
    //   bad anyway).
    ClassLoader cl = trusted_get_class_loader.getClassLoader(c);

    return isTrustedClassLoader(cl);

  }

  // ----- Threads -----

  @Override
  public void checkCreateClassLoader() {
    super.checkCreateClassLoader();
  }

  @Override
  public void checkAccess(Thread t) {
    checkPermission(MODIFY_THREAD_PERMISSION);
  }

  @Override
  public void checkAccess(ThreadGroup t) {
    checkPermission(MODIFY_THREADGROUP_PERMISSION);
  }

  /**
   * This is called when a caller is using reflection to access an object. If
   * a request is made on an object from the same classloader as the caller,
   * no security check is performed. Otherwise, if the request is for PUBLIC
   * member access, the request is allowed if the class is white listed and
   * the security manager allows it. If the request is for DECLARED member
   * access, the request is allowed if the security manager allows it.
   */
  @Override
  public void checkMemberAccess(final Class clazz, final int which) {

    try {

      if (clazz == null) {
        throw new NullPointerException("class can't be null");
      }

      // All reflective access to public fields and methods is allowed.
      if (which == Member.PUBLIC) {
        return;
      }

      Class[] stack = getClassContext();
      if (stack.length < 4) {
        checkPermission(ACCESS_DECLARED_MEMBERS_PERMISSION);
        return;
      }

      // The class loader of the class,
      ClassLoader cl = trusted_get_class_loader.getClassLoader(clazz);
      // If the class loader of the class being accessed is not trusted, then
      // it's also assumed it's not protected,
      if (!isTrustedClassLoader(cl)) {
        return;
      }

      // The calling class loader (null if bootstrap),
      ClassLoader ccl = trusted_get_class_loader.getClassLoader(stack[3]);

      // If the class loaders are the same, we allow regardless,
      // If they are different,
      if (ccl != cl) {
        // If it's public member access,
        if (which == Member.PUBLIC) {

        }
        if (which != Member.PUBLIC) {

          // If the request is NOT public (ie. DECLARED) then check the runtime
          // permission.
          checkPermission(ACCESS_DECLARED_MEMBERS_PERMISSION);

        }
      }
    }
    catch (SecurityException e) {
      System.err.println("SE(checkMemberAccess): " + e.getMessage());
//      e.printStackTrace(System.err);
      throw e;
    }

  }

  @Override
  public void checkPackageAccess(String pkg) {

    try {
      // TODO: Should we check package access against a whitelist here?
      super.checkPackageAccess(pkg);
    }
    catch (SecurityException e) {
      System.err.println("SE(checkPackageAccess): " + e.getMessage());
      e.printStackTrace(System.err);
      throw e;
    }
  }










  // ----- Other permissions ------

  private static class TrustedGetClassLoader implements SMTrustedAction {
    private ClassLoader getClassLoader(Class c) {
      return c.getClassLoader();
    }
  }
  private final TrustedGetClassLoader trusted_get_class_loader =
                                                 new TrustedGetClassLoader();

  private static class TrustedGetSystemClassLoader implements SMTrustedAction {
    private ClassLoader getSystemClassLoader() {
      return ClassLoader.getSystemClassLoader();
    }
  }
  private final TrustedGetSystemClassLoader trusted_get_system_class_loader =
                                           new TrustedGetSystemClassLoader();

  private static class TrustedGetDomain implements SMTrustedAction {
    private ProtectionDomain getProtectionDomain(Class c) {
      return c.getProtectionDomain();
    }
  }
  private final TrustedGetDomain trusted_get_domain = new TrustedGetDomain();

  private static class TrustedGetCodeSource implements SMTrustedAction {
    private CodeSource getCodeSource(ProtectionDomain domain) {
      return domain.getCodeSource();
    }
  }
  private final TrustedGetCodeSource trusted_get_code_source =
                                                  new TrustedGetCodeSource();




  @Override
  public void checkPermission(final Permission perm) {

    // The current policy
    MckoiDDBAppPolicy policy = MckoiDDBAppPolicy.getCurrentMckoiPolicy();

    // Fast path: User level permissions are implied by default,
    boolean is_user_permitted = policy.userLevelPermissions().implies(perm);
    if (is_user_permitted) {
      return;
    }

    final boolean trace = false;
    final StringBuilder trace_out = trace ? new StringBuilder() : null;

//    if (perm instanceof java.io.FilePermission) {
//      String perm_msg = perm.toString();
//      trace = true;
//      System.err.println("check file permission: " + perm_msg);
//      return;
//    }

    // Ok, basically this is what we do;
    // 1) Walk backwards down the stack frame collecting ProtectionDomain
    //    objects. If a permission fails on a protection domain, we throw a
    //    security exception.
    // 2) If an instance of java.security.Privileged* is encountered, or if
    //    a TrustedObject is encountered, we do no further checks.
    // 3) An 'SMTrustedAction' class on the stack bypasses all security
    //    checks (these only happen for certain security manager related
    //    operations).

    Class[] context_stack = getClassContext();

    // If there's any SMTrustedAction instances on the stack, we can trust the
    // call (this would be a callback from a security manager operation).
    int limit = Math.min(24, context_stack.length);
    for (int i = 0; i < limit; ++i) {
      if (SMTrustedAction.class.isAssignableFrom(context_stack[i])) {
        // Check the operation just to make sure (strictly not necessary but
        // it's security related so best to make sure),
        String name = perm.getName();
        if (perm instanceof RuntimePermission &&
            (name.equals("getProtectionDomain") ||
             name.equals("getClassLoader"))) {
          return;
        }
      }
    }

    // Remember domains visited.
    ArrayList<ProtectionDomain> domains = new ArrayList<>(6);

    int perms_checked = 0;
    int frame_position = 0;

    // For each frame on the stack,
    for (Class contxt : context_stack) {
      ++frame_position;

      // Skip past the security manager entries on the stack,
      if (contxt == MckoiDDBAppSecurityManager.class) {
        if (trace_out != null) {
          trace_out.append(" ").append("<MckoiDDBAppSecurityManager>\n");
        }
      }
      // if (contxt != MckoiDDBAppSecurityManager.class) {
      else {
        // Get the protection domain,
        // NOTE: This can't be trusted if untrusted code can create class
        //   loaders. We can trust the ProtectionDomain to determine if an
        //   operation should be denied however (since faking a
        //   ProtectionDomain to deny an operation would not aid in a privilege
        //   escalation attack).
        ProtectionDomain domain = trusted_get_domain.getProtectionDomain(contxt);
        if (trace_out != null) {
          trace_out.append("ProtectionDomain\n");
          trace_out.append(" CodeSource: ").append(
                  trusted_get_code_source.getCodeSource(domain)).append("\n");
          trace_out.append(" Permissions: ").
                  append(domain.getPermissions()).append("\n");
          trace_out.append(" ClassLoader: ").
                  append(domain.getClassLoader()).append("\n");
          trace_out.append(" Principles count: ").
                  append(domain.getPrincipals().length).append("\n");
        }
        // If the protection domain is null, or the code source is null, then
        // assume the class is the bootstrap class and keep going.
        if (domain != null) {
          // Did we check this domain already?
          if (!domains.contains(domain)) {
            domains.add(domain);
            // If the permissions do not imply the given permission, generate
            // an exception.
            ++perms_checked;
            if (!domain.implies(perm)) {

              // The security exception,
              SecurityException se = new SecurityException(
                  MessageFormat.format("access denied: {0} [frame: {1}]",
                                       perm, frame_position));

              if (trace_out != null) {
                // If it's from a trusted class loader then report the security
                // exception to System.err.
                // This helps work out the security privs that should be
                // allowed to SYSTEM.
                ClassLoader cl = trusted_get_class_loader.getClassLoader(contxt);
                if (this.isTrustedClassLoader(cl)) {
                  System.err.println("SE: " + perm);
                  System.err.println("  on class: " + contxt.getName());
                  se.printStackTrace(System.err);
                }

                System.err.println("FAILED: " + perm);
                System.err.println(trace_out.toString());
              }

              throw se;
            }
          }
        }

        // If the class is a privileged or trusted object, then we need to
        // decide if we should exit or throw a security exception. A
        // privileged action that happens from an untrusted class loader can
        // only be allowed if the user permissions grant it.

        if (PrivilegedAction.class.isAssignableFrom(contxt) ||
            PrivilegedExceptionAction.class.isAssignableFrom(contxt)
           ) {

          // If the classloader of the object is not trusted,
          if (!fromTrustedClassLoader(contxt)) {
            // Not from a trusted classloader,
            System.out.println("SE (bcl): " + perm);
            SecurityException se = new SecurityException(
                    MessageFormat.format(
                        "access denied (bad class loader): {0} [frame: {1}]",
                        perm, frame_position));
            throw se;
          }
          if (perms_checked == 0) {
            System.out.println("EXITING (no checks) on " + perm);
          }
          return;
        }

      }
    }
    if (perms_checked == 0) {
      System.out.println("EXITING (no checks) on " + perm);
    }
  }


  private interface SMTrustedAction {
  }

}
